package ir.mehradn.cavesurvey.util;

import net.minecraft.world.item.MapItem;

/**
 * A copy of a cave map's pixel anchors, addressed by block column, for a survey on any thread.
 *
 * @param heights each pixel's anchor height, at {@link #index}, or {@link #NONE}
 * @param centerX the map's centre x
 * @param centerZ the map's centre z
 * @param scaleShift the map's scale: each pixel covers {@code 1 << scaleShift} blocks a side
 */
public record PixelAnchors(short[] heights, int centerX, int centerZ, int scaleShift) {
    /** The anchor of a pixel no survey has drawn yet. */
    public static final int NONE = Short.MIN_VALUE;
    private static final int MAP_SIZE = MapItem.IMAGE_WIDTH;
    /** How many anchors a map has, one per pixel. */
    public static final int PIXELS = MAP_SIZE * MAP_SIZE;

    /** Returns pixel (pixelX, pixelZ)'s place in an anchor array, which is the saved layout. */
    public static int index(int pixelX, int pixelZ) {
        return pixelX * MAP_SIZE + pixelZ;
    }

    /** Returns the anchor of the pixel holding block column (x, z), or {@link #NONE}. */
    public int atColumn(int x, int z) {
        int scale = 1 << this.scaleShift;
        return atPixel(Math.floorDiv(x, scale) - this.centerX / scale + MAP_SIZE / 2,
                Math.floorDiv(z, scale) - this.centerZ / scale + MAP_SIZE / 2);
    }

    /** Returns the anchor of pixel (pixelX, pixelZ), or {@link #NONE} off the map. */
    public int atPixel(int pixelX, int pixelZ) {
        if (pixelX < 0 || pixelZ < 0 || pixelX >= MAP_SIZE || pixelZ >= MAP_SIZE) {
            return NONE;
        }
        return this.heights[index(pixelX, pixelZ)];
    }
}
