package ir.mehradn.cavesurvey.util;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.material.MapColor;

/**
 * Turns a survey into map pixel colours, with the shading vanilla maps use. Reads only its
 * arguments, so a worker thread can run it.
 */
public final class CaveMapDrawing {
    private static final int MAP_SIZE = MapItem.IMAGE_WIDTH;
    // Every MapColor id is below this (MapColor.MATERIAL_COLORS).
    private static final int MAP_COLOR_COUNT = 64;

    private CaveMapDrawing() {
    }

    /**
     * Returns whether a survey of {@code blockRadius} from {@code headPos} can touch the map.
     *
     * @param scaleShift the map's scale: each pixel covers {@code 1 << scaleShift} blocks a side
     */
    public static boolean reaches(BlockPos headPos, int blockRadius, int centerX, int centerZ,
            int scaleShift) {
        int scale = 1 << scaleShift;
        int relativeX = (headPos.getX() - centerX) / scale + MAP_SIZE / 2;
        int relativeZ = (headPos.getZ() - centerZ) / scale + MAP_SIZE / 2;
        int pixelRadius = blockRadius / scale;
        return !(MAP_SIZE + pixelRadius + 4 < relativeX || relativeX < -pixelRadius - 4
                || MAP_SIZE + pixelRadius + 4 < relativeZ || relativeZ < -pixelRadius - 4);
    }

    /**
     * Returns the pixels a survey sets, each packed as {@code pixelX << 16 | pixelZ << 8 | colour};
     * read them back with {@link #pixelX}, {@link #pixelZ} and {@link #color}. Adds each set
     * pixel's new anchor height to {@code anchorsOut}, in the same order.
     *
     * @param anchors the map's anchors before this survey, which also give its centre and scale
     * @param drawsOpenSky whether to draw floors open to the sky; if not, such a pixel gets the
     *     ceiling static ({@link #noiseColor}) and keeps its anchor
     */
    public static IntArrayList draw(CaveMappingAlgorithm.PixelMatrix matrix, BlockPos headPos,
            int blockRadius, PixelAnchors anchors, IntArrayList anchorsOut, boolean drawsOpenSky) {
        int scale = 1 << anchors.scaleShift();
        int centerX = anchors.centerX();
        int centerZ = anchors.centerZ();
        int relativeX = (headPos.getX() - centerX) / scale + MAP_SIZE / 2;
        int relativeZ = (headPos.getZ() - centerZ) / scale + MAP_SIZE / 2;
        int pixelRadius = blockRadius / scale;
        IntArrayList updates = new IntArrayList();
        int[] colorCounts = new int[MAP_COLOR_COUNT];
        int[] seenColors = new int[scale * scale];

        for (int pixelX = relativeX - pixelRadius; pixelX < relativeX + pixelRadius; pixelX++) {
            int firstPixelZ = relativeZ - pixelRadius;
            double previousAverageHeight = storedHeight(anchors, pixelX, firstPixelZ - 1);
            for (int pixelZ = firstPixelZ; pixelZ < relativeZ + pixelRadius; pixelZ++) {
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
                    // Unreached this time: shade the next pixel against what the map last drew.
                    previousAverageHeight = storedHeight(anchors, pixelX, pixelZ);
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

                byte packed;
                if (!drawsOpenSky && brightCount * 2 >= pixelCount) {
                    // Vanilla's static is flat, so it always shades normal.
                    packed = noiseColor(realX, realZ).getPackedId(MapColor.Brightness.NORMAL);
                } else {
                    MapColor.Brightness brightness = switch (brightnessLevel) {
                        case 0 -> MapColor.Brightness.LOWEST;
                        case 1 -> MapColor.Brightness.LOW;
                        case 2 -> MapColor.Brightness.NORMAL;
                        default -> MapColor.Brightness.HIGH;
                    };
                    packed = color.getPackedId(brightness);
                }
                updates.add(pixelX << 16 | pixelZ << 8 | packed & 0xff);
                anchorsOut.add((int) Math.round(averageHeight) + 1);
            }
        }
        return updates;
    }

    /**
     * Returns the colour of the static a vanilla map draws under a ceiling, for the area whose
     * lowest corner is ({@code areaMinX}, {@code areaMinZ}): dirt or stone from a hash of it.
     */
    public static MapColor noiseColor(int areaMinX, int areaMinZ) {
        int noise = areaMinX + areaMinZ * 231871;
        noise = noise * noise * 31287121 + noise * 11;
        return (noise >> 20 & 1) == 0 ? MapColor.DIRT : MapColor.STONE;
    }

    /** Returns the pixel column of a packed update. */
    public static int pixelX(int update) {
        return update >>> 16 & 0xff;
    }

    /** Returns the pixel row of a packed update. */
    public static int pixelZ(int update) {
        return update >>> 8 & 0xff;
    }

    /** Returns the packed map colour of a packed update. */
    public static byte color(int update) {
        return (byte) update;
    }

    // The floor height the map last drew at a pixel, or 0 (upstream's start value) for none.
    private static double storedHeight(PixelAnchors anchors, int pixelX, int pixelZ) {
        int anchor = anchors.atPixel(pixelX, pixelZ);
        return anchor == PixelAnchors.NONE ? 0.0 : anchor - 1;
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
