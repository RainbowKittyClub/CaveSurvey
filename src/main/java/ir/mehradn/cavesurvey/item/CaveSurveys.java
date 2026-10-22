package ir.mehradn.cavesurvey.item;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import ir.mehradn.cavesurvey.CaveSurvey;
import ir.mehradn.cavesurvey.util.CaveMapDrawing;
import ir.mehradn.cavesurvey.util.CaveMappingAlgorithm;
import ir.mehradn.cavesurvey.util.PixelAnchors;

/**
 * Runs cave map surveys off the server thread. The server thread copies the chunks a survey can
 * read, a worker floods and shades them, and the server thread writes the result into the map.
 */
public final class CaveSurveys {
    private static final Logger LOGGER = LoggerFactory.getLogger(CaveSurvey.MOD_ID);
    // Logs each survey's cost in the dev environment, or with the JVM property set.
    private static final boolean LOG_TIMING = FabricLoader.getInstance().isDevelopmentEnvironment()
            || Boolean.getBoolean("cavesurvey.debugScanTiming");
    // Ticks between a held map's surveys while its holder moves, and while they stand still.
    private static final int MOVING_SURVEY_TICKS = 20;
    private static final int STILL_SURVEY_TICKS = 100;
    // Maps with a survey between its snapshot and its write; one at a time per map.
    private static final Set<MapId> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    // Each holder's last survey. Vanilla drops a map's holder once the map leaves their
    // inventory, and the weak key goes with it. Server thread only.
    private static final Map<MapItemSavedData.HoldingPlayer, LastSurvey> LAST_SURVEYS =
            new WeakHashMap<>();
    private static volatile ExecutorService worker;

    private CaveSurveys() {
    }

    /** Starts the worker with each server and stops it as the server stops. */
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> start());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stop());
    }

    /**
     * Surveys the cave map {@code stack} that {@code player} holds, when due: a second after their
     * last survey of it once their head has moved to another block, else five seconds after. A
     * turn that another holder's survey of the same map blocks comes on the next tick.
     */
    public static void requestHeld(ServerLevel level, Player player, ItemStack stack,
            MapItemSavedData data) {
        LastSurvey last = LAST_SURVEYS.get(data.getHoldingPlayer(player));
        if (last != null) {
            boolean moved = last.head() != headPos(player).asLong();
            int since = level.getServer().getTickCount() - last.tick();
            if (since < (moved ? MOVING_SURVEY_TICKS : STILL_SURVEY_TICKS)) {
                return;
            }
        }
        request(level, player, stack);
    }

    /** Returns the block a survey starts from, and its holder is placed by: the player's head. */
    public static BlockPos headPos(Player player) {
        return player.blockPosition().above();
    }

    /**
     * Surveys the cave around the player's head into the cave map {@code stack}, off the server
     * thread. Returns at once; the map fills a moment later. Does nothing while a survey of the
     * same map is still running, or for a locked or missing map.
     */
    public static void request(ServerLevel level, Player player, ItemStack stack) {
        ExecutorService executor = worker;
        MapId id = stack.get(DataComponents.MAP_ID);
        MapItemSavedData data = MapItem.getSavedData(id, level);
        if (executor == null || data == null || data.locked
                || level.dimension() != data.dimension
                || !(stack.getItem() instanceof CaveMapItem map) || !map.surveys(data)) {
            return;
        }
        BlockPos headPos = headPos(player);
        int sight = CaveMapSight.get(stack);
        int blockRadius = CaveMapSight.blockRadius(sight);
        int centerX = data.centerX;
        int centerZ = data.centerZ;
        int scaleShift = data.scale;
        if (!CaveMapDrawing.reaches(headPos, blockRadius, centerX, centerZ, scaleShift)) {
            return;
        }
        if (!IN_FLIGHT.add(id)) {
            if (LOG_TIMING) {
                LOGGER.info("Cave Survey scan skipped: map {} has one running", id.id());
            }
            return;
        }

        try {
            long snapshotStart = System.nanoTime();
            MinecraftServer server = level.getServer();
            CaveMappingAlgorithm.Snapshot snapshot = CaveMappingAlgorithm.Snapshot.take(level,
                    headPos.getX(), headPos.getZ(), blockRadius);
            PixelAnchors anchors = new PixelAnchors(CaveMapAnchors.of(server, id).copy(), centerX,
                    centerZ, scaleShift);
            int feetY = player.getBlockY();
            boolean coveredFirst = !map.kind().drawsOpenSky();
            long snapshotNanos = System.nanoTime() - snapshotStart;
            executor.execute(() -> {
                long workStart = System.nanoTime();
                IntArrayList updates = null;
                IntArrayList newAnchors = new IntArrayList();
                try {
                    CaveMappingAlgorithm.PixelMatrix matrix = CaveMappingAlgorithm.run(headPos,
                            blockRadius, feetY, anchors, snapshot, coveredFirst);
                    updates = CaveMapDrawing.draw(matrix, headPos, blockRadius, anchors,
                            newAnchors);
                } catch (RuntimeException e) {
                    LOGGER.error("Cave Survey scan of map {} failed", id.id(), e);
                } finally {
                    // Even after an Error, so the map's guard clears.
                    long workNanos = System.nanoTime() - workStart;
                    IntArrayList done = updates;
                    server.execute(() -> apply(level, id, done, newAnchors, snapshotNanos,
                            workNanos, sight));
                }
            });
            LAST_SURVEYS.put(data.getHoldingPlayer(player),
                    new LastSurvey(server.getTickCount(), headPos.asLong()));
        } catch (RuntimeException e) {
            IN_FLIGHT.remove(id);
            // Rejected only once the worker has stopped with the server.
            if (!(e instanceof RejectedExecutionException)) {
                LOGGER.error("Cave Survey scan of map {} could not start", id.id(), e);
            }
        }
    }

    // Writes a finished survey and its anchors into the map, unless the map has gone or been
    // locked meanwhile.
    private static void apply(ServerLevel level, MapId id, IntArrayList updates,
            IntArrayList newAnchors, long snapshotNanos, long workNanos, int sight) {
        long applyStart = System.nanoTime();
        IN_FLIGHT.remove(id);
        if (updates == null || worker == null) {
            return;
        }
        MapItemSavedData data = level.getMapData(id);
        if (data == null || data.locked) {
            return;
        }
        CaveMapAnchors anchors = CaveMapAnchors.of(level.getServer(), id);
        for (int i = 0; i < updates.size(); i++) {
            int update = updates.getInt(i);
            int pixelX = CaveMapDrawing.pixelX(update);
            int pixelZ = CaveMapDrawing.pixelZ(update);
            data.updateColor(pixelX, pixelZ, CaveMapDrawing.color(update));
            anchors.set(pixelX, pixelZ, newAnchors.getInt(i));
        }
        if (LOG_TIMING) {
            double serverMs = (snapshotNanos + System.nanoTime() - applyStart) / 1e6;
            LOGGER.info("Cave Survey scan: {} ms on the server thread, {} ms on the worker, "
                    + "sight {}", String.format(Locale.ROOT, "%.3f", serverMs),
                    String.format(Locale.ROOT, "%.3f", workNanos / 1e6), sight);
        }
    }

    // A single daemon thread, so surveys never hold up shutdown and never contend for each other.
    private static void start() {
        worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "cave-survey-scan");
            thread.setDaemon(true);
            return thread;
        });
    }

    // Drops queued surveys; one already running finishes, and its write is never applied.
    private static void stop() {
        ExecutorService executor = worker;
        worker = null;
        if (executor != null) {
            executor.shutdownNow();
        }
        IN_FLIGHT.clear();
        LAST_SURVEYS.clear();
    }

    // When a holder last surveyed a map: the server tick, and their head's block position.
    private record LastSurvey(int tick, long head) {
    }
}
