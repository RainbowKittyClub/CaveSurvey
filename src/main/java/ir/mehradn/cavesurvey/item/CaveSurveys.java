package ir.mehradn.cavesurvey.item;

import java.util.Locale;
import java.util.Set;
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
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import ir.mehradn.cavesurvey.CaveSurvey;
import ir.mehradn.cavesurvey.util.CaveMapDrawing;
import ir.mehradn.cavesurvey.util.CaveMappingAlgorithm;

/**
 * Runs cave map surveys off the server thread. The server thread copies the chunks a survey can
 * read, a worker floods and shades them, and the server thread writes the result into the map.
 */
public final class CaveSurveys {
    private static final Logger LOGGER = LoggerFactory.getLogger(CaveSurvey.MOD_ID);
    // Logs each survey's cost in the dev environment, or with the JVM property set.
    private static final boolean LOG_TIMING = FabricLoader.getInstance().isDevelopmentEnvironment()
            || Boolean.getBoolean("cavesurvey.debugScanTiming");
    // Maps with a survey between its snapshot and its write; one at a time per map.
    private static final Set<MapId> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static volatile ExecutorService worker;

    private CaveSurveys() {
    }

    /** Starts the worker with each server and stops it as the server stops. */
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> start());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stop());
    }

    /**
     * Surveys the cave around the player's head into the cave map {@code stack}, off the server
     * thread. Returns at once; the map fills a moment later. Does nothing while a survey of the
     * same map is still running, or for a locked or missing map.
     */
    public static void request(ServerLevel level, Player player, ItemStack stack) {
        ExecutorService executor = worker;
        MapId id = stack.get(DataComponents.MAP_ID);
        MapItemSavedData data = id == null ? null : level.getMapData(id);
        if (executor == null || data == null || data.locked
                || level.dimension() != data.dimension) {
            return;
        }
        BlockPos headPos = player.blockPosition().above();
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

        long snapshotStart = System.nanoTime();
        CaveMappingAlgorithm.Snapshot snapshot = CaveMappingAlgorithm.Snapshot.take(level,
                headPos.getX(), headPos.getZ(), blockRadius);
        long snapshotNanos = System.nanoTime() - snapshotStart;
        MinecraftServer server = level.getServer();
        try {
            executor.execute(() -> {
                long workStart = System.nanoTime();
                IntArrayList updates = null;
                try {
                    CaveMappingAlgorithm.PixelMatrix matrix =
                            CaveMappingAlgorithm.run(headPos, blockRadius, snapshot);
                    updates = CaveMapDrawing.draw(matrix, headPos, blockRadius, centerX, centerZ,
                            scaleShift);
                } catch (RuntimeException e) {
                    LOGGER.error("Cave Survey scan of map {} failed", id.id(), e);
                }
                long workNanos = System.nanoTime() - workStart;
                IntArrayList done = updates;
                server.execute(() -> apply(level, id, done, snapshotNanos, workNanos, sight));
            });
        } catch (RejectedExecutionException e) {
            IN_FLIGHT.remove(id);
        }
    }

    // Writes a finished survey into its map, unless the map has gone or been locked meanwhile.
    private static void apply(ServerLevel level, MapId id, IntArrayList updates, long snapshotNanos,
            long workNanos, int sight) {
        long applyStart = System.nanoTime();
        IN_FLIGHT.remove(id);
        if (updates == null || worker == null) {
            return;
        }
        MapItemSavedData data = level.getMapData(id);
        if (data == null || data.locked) {
            return;
        }
        for (int i = 0; i < updates.size(); i++) {
            int update = updates.getInt(i);
            data.updateColor(CaveMapDrawing.pixelX(update), CaveMapDrawing.pixelZ(update),
                    CaveMapDrawing.color(update));
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
    }
}
