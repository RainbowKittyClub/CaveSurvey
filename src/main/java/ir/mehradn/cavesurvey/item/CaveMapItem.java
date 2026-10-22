package ir.mehradn.cavesurvey.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import ir.mehradn.cavesurvey.CaveSurvey;
import ir.mehradn.cavesurvey.util.CaveMappingAlgorithm;

/** A filled cave map: each use surveys the cave around the player into the map. */
public class CaveMapItem extends MapItem implements PolymerItem {
    /** The width and height of a map in pixels. */
    public static final int MAP_SIZE = 128;
    private static final int BANNER_BLOCK_RADIUS = 64;
    // Every MapColor id is below this (MapColor.MATERIAL_COLORS).
    private static final int MAP_COLOR_COUNT = 64;
    private static final Logger LOGGER = LoggerFactory.getLogger(CaveSurvey.MOD_ID);
    // Logs each survey's server-thread time in the dev environment, or with the JVM property set.
    private static final boolean LOG_SCAN_TIMING =
            FabricLoader.getInstance().isDevelopmentEnvironment()
                    || Boolean.getBoolean("cavesurvey.debugScanTiming");

    /** Creates the item with the given properties. */
    public CaveMapItem(Item.Properties properties) {
        super(properties);
    }

    /** Creates a filled cave map at scale 0 centred on the given block column. */
    public static ItemStack create(ServerLevel level, int x, int z) {
        ItemStack stack = new ItemStack(CaveMapItems.FILLED_CAVE_MAP);
        MapItem.applyNewSavedData(level, stack, x, z, (byte) 0, true, false);
        return stack;
    }

    /** Surveys the cave around the viewer's head and draws it into the map. */
    public static void updateMap(Level level, Entity viewer, MapItemSavedData data, int sight) {
        if (level.dimension() != data.dimension || !(viewer instanceof Player player)) {
            return;
        }

        BlockPos headPos = player.blockPosition().above();
        int scale = 1 << data.scale;
        int centerX = data.centerX;
        int centerZ = data.centerZ;
        int relativeX = (headPos.getX() - centerX) / scale + MAP_SIZE / 2;
        int relativeZ = (headPos.getZ() - centerZ) / scale + MAP_SIZE / 2;
        int blockRadius = CaveMapSight.blockRadius(sight);
        int pixelRadius = blockRadius / scale;

        if (MAP_SIZE + pixelRadius + 4 < relativeX || relativeX < -pixelRadius - 4
                || MAP_SIZE + pixelRadius + 4 < relativeZ || relativeZ < -pixelRadius - 4) {
            return;
        }
        long startNanos = System.nanoTime();
        CaveMappingAlgorithm.PixelMatrix matrix =
                CaveMappingAlgorithm.run(headPos, blockRadius, level);
        int[] colorCounts = new int[MAP_COLOR_COUNT];
        int[] seenColors = new int[scale * scale];

        for (int pixelX = relativeX - pixelRadius; pixelX < relativeX + pixelRadius; pixelX++) {
            double previousAverageHeight = 0.0;
            for (int pixelZ = relativeZ - pixelRadius; pixelZ < relativeZ + pixelRadius; pixelZ++) {
                if (pixelX < 0 || pixelZ < 0 || pixelX >= MAP_SIZE || pixelZ >= MAP_SIZE) {
                    continue;
                }
                int distance = Mth.square(pixelX - relativeX) + Mth.square(pixelZ - relativeZ);
                int realX = (centerX / scale + pixelX - MAP_SIZE / 2) * scale;
                int realZ = (centerZ / scale + pixelZ - MAP_SIZE / 2) * scale;

                double averageHeight = 0.0;
                double averageFluidDepth = 0.0;
                int pixelCount = 0;
                int brightCount = 0;
                int seen = 0;
                for (int innerX = 0; innerX < scale; innerX++) {
                    for (int innerZ = 0; innerZ < scale; innerZ++) {
                        int x = realX + innerX;
                        int z = realZ + innerZ;
                        if (!matrix.reached(x, z)) {
                            continue;
                        }

                        pixelCount++;
                        if (matrix.reachesSky(x, z)) {
                            brightCount++;
                        }
                        averageFluidDepth += matrix.fluidDepth(x, z);
                        averageHeight += matrix.floorY(x, z);
                        int colorId = matrix.colorId(x, z);
                        if (colorCounts[colorId]++ == 0) {
                            seenColors[seen++] = colorId;
                        }
                    }
                }

                if (pixelCount == 0) {
                    previousAverageHeight = 0;
                    continue;
                }
                averageHeight /= pixelCount;
                averageFluidDepth /= pixelCount;

                MapColor color = MapColor.byId(mostCommonColor(colorCounts, seenColors, seen));

                int brightnessLevel;
                if (color == MapColor.WATER) {
                    double darkness =
                            averageFluidDepth * 0.1 + (double) (pixelX + pixelZ & 1) * 0.2;
                    if (darkness > 0.9) {
                        brightnessLevel = 0;
                    } else if (darkness > 0.5) {
                        brightnessLevel = 1;
                    } else {
                        brightnessLevel = 2;
                    }
                } else {
                    double brightness = (averageHeight - previousAverageHeight) * 4.0
                            / (double) (scale + 4) + ((double) (pixelX + pixelZ & 1) - 0.5) * 0.4;
                    if (brightness < -0.6) {
                        brightnessLevel = 0;
                    } else if (brightness < 0.6) {
                        brightnessLevel = 1;
                    } else {
                        brightnessLevel = 2;
                    }
                }
                if (brightCount >= pixelCount / 2) {
                    brightnessLevel++;
                }

                previousAverageHeight = averageHeight;
                if (pixelCount < Mth.square(scale) / 2 || distance >= Mth.square(pixelRadius)) {
                    continue;
                }

                MapColor.Brightness brightness = switch (brightnessLevel) {
                    case 0 -> MapColor.Brightness.LOWEST;
                    case 1 -> MapColor.Brightness.LOW;
                    case 2 -> MapColor.Brightness.NORMAL;
                    default -> MapColor.Brightness.HIGH;
                };
                data.updateColor(pixelX, pixelZ, color.getPackedId(brightness));
            }
        }
        if (LOG_SCAN_TIMING) {
            LOGGER.info("Cave Survey scan: {} ms, sight {}", String.format(Locale.ROOT, "%.3f",
                    (System.nanoTime() - startNanos) / 1e6), sight);
        }
    }

    /** Drops banner markers whose banner no longer stands, near the viewer. */
    public static void updateBanners(Level level, Entity viewer, MapItemSavedData data) {
        if (level.dimension() != data.dimension || !(viewer instanceof Player player)) {
            return;
        }

        BlockPos headPos = player.blockPosition().above();
        int scale = 1 << data.scale;
        int centerX = data.centerX;
        int centerZ = data.centerZ;
        int relativeX = (headPos.getX() - centerX) / scale + MAP_SIZE / 2;
        int relativeZ = (headPos.getZ() - centerZ) / scale + MAP_SIZE / 2;
        int pixelRadius = BANNER_BLOCK_RADIUS / scale;

        if (MAP_SIZE + pixelRadius + 4 < relativeX || relativeX < -pixelRadius - 4
                || MAP_SIZE + pixelRadius + 4 < relativeZ || relativeZ < -pixelRadius - 4) {
            return;
        }
        // The square of pixels near the viewer, clipped to the map, as blocks.
        int minPixelX = Math.max(relativeX - pixelRadius, 0);
        int maxPixelX = Math.min(relativeX + pixelRadius - 1, MAP_SIZE - 1);
        int minPixelZ = Math.max(relativeZ - pixelRadius, 0);
        int maxPixelZ = Math.min(relativeZ + pixelRadius - 1, MAP_SIZE - 1);
        if (minPixelX > maxPixelX || minPixelZ > maxPixelZ) {
            return;
        }
        int minX = (centerX / scale + minPixelX - MAP_SIZE / 2) * scale;
        int maxX = (centerX / scale + maxPixelX - MAP_SIZE / 2) * scale + scale - 1;
        int minZ = (centerZ / scale + minPixelZ - MAP_SIZE / 2) * scale;
        int maxZ = (centerZ / scale + maxPixelZ - MAP_SIZE / 2) * scale + scale - 1;

        // checkBanners walks every marker on each call, so call it only for markers in the square.
        // Collected first, since checkBanners removes from the collection being walked.
        List<BlockPos> inSquare = new ArrayList<>();
        for (MapBanner banner : data.getBanners()) {
            BlockPos pos = banner.pos();
            if (pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ) {
                inSquare.add(pos);
            }
        }
        for (BlockPos pos : inSquare) {
            data.checkBanners(level, pos.getX(), pos.getZ());
        }
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
        return Items.FILLED_MAP;
    }

    @Override
    public void update(Level level, Entity player, MapItemSavedData data) {
        updateBanners(level, player, data);
    }

    @Override
    public void onCraftedBy(ItemStack itemStack, Player player) {
        // Super applies any scale or lock post-processing first, so this surveys the new map.
        super.onCraftedBy(itemStack, player);
        MapItemSavedData data = MapItem.getSavedData(itemStack, player.level());
        if (data != null && !data.locked) {
            updateMap(player.level(), player, data, CaveMapSight.get(itemStack));
        }
    }

    @Override
    public void appendHoverText(ItemStack itemStack, Item.TooltipContext context,
            TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        MapId id = itemStack.get(DataComponents.MAP_ID);
        MapItemSavedData data = id == null ? null : context.mapData(id);
        if (data == null || data.locked
                || itemStack.get(DataComponents.MAP_POST_PROCESSING) == MapPostProcessing.LOCK) {
            return;
        }
        String key = switch (CaveMapSight.get(itemStack)) {
            case 0 -> "filled_cave_map.low_sight";
            case 1 -> "filled_cave_map.medium_sight";
            default -> "filled_cave_map.high_sight";
        };
        builder.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        MapItemSavedData data = MapItem.getSavedData(itemStack, level);
        if (data == null) {
            return InteractionResult.FAIL;
        }
        if (data.locked) {
            return InteractionResult.CONSUME;
        }
        updateMap(level, player, data, CaveMapSight.get(itemStack));
        return InteractionResult.SUCCESS;
    }

    // The most counted of the first `seen` colour ids, the earliest seen on a tie, as a
    // count-sorted LinkedHashMultiset gives; clears the counts it read for the next pixel.
    private static int mostCommonColor(int[] counts, int[] seenIds, int seen) {
        int best = seenIds[0];
        for (int i = 1; i < seen; i++) {
            if (counts[seenIds[i]] > counts[best]) {
                best = seenIds[i];
            }
        }
        for (int i = 0; i < seen; i++) {
            counts[seenIds[i]] = 0;
        }
        return best;
    }
}
