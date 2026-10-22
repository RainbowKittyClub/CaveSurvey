package ir.mehradn.cavesurvey.item;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.mojang.serialization.Codec;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.saveddata.maps.MapId;

import ir.mehradn.cavesurvey.CaveSurvey;
import ir.mehradn.cavesurvey.util.PixelAnchors;

/**
 * Which air pocket each pixel of a cave map shows: the height of the air block above the floor it
 * last drew. Kept per map id beside vanilla's map data, so clones share it as they share the
 * picture.
 */
public final class CaveMapAnchors extends SavedData {
    private static final int PIXELS = PixelAnchors.PIXELS;
    private static final Codec<CaveMapAnchors> CODEC = Codec.BYTE_BUFFER.fieldOf("heights").codec()
            .xmap(CaveMapAnchors::fromBytes, CaveMapAnchors::toBytes);

    private final short[] heights;

    private CaveMapAnchors(short[] heights) {
        this.heights = heights;
    }

    /** Returns the anchors of map {@code id}, creating empty ones on first use. */
    public static CaveMapAnchors of(MinecraftServer server, MapId id) {
        return server.getDataStorage().computeIfAbsent(type(id));
    }

    /** Returns a copy of every pixel's anchor, at {@link PixelAnchors#index}. */
    public short[] copy() {
        return this.heights.clone();
    }

    /** Sets one pixel's anchor. */
    public void set(int pixelX, int pixelZ, int height) {
        int index = PixelAnchors.index(pixelX, pixelZ);
        if (this.heights[index] != height) {
            this.heights[index] = (short) height;
            setDirty();
        }
    }

    // The saved-data type for map id; command storage is the vanilla type with no fixer that
    // reads into its contents.
    private static SavedDataType<CaveMapAnchors> type(MapId id) {
        return new SavedDataType<>(CaveSurvey.id("anchors/" + id.id()), CaveMapAnchors::empty,
                CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    }

    // Anchors for a map no survey has drawn.
    private static CaveMapAnchors empty() {
        short[] heights = new short[PIXELS];
        Arrays.fill(heights, (short) PixelAnchors.NONE);
        return new CaveMapAnchors(heights);
    }

    // Big-endian shorts; a wrong length, from a damaged file, starts the map's anchors afresh.
    private static CaveMapAnchors fromBytes(ByteBuffer bytes) {
        if (bytes.remaining() != PIXELS * 2) {
            return empty();
        }
        short[] heights = new short[PIXELS];
        bytes.asShortBuffer().get(heights);
        return new CaveMapAnchors(heights);
    }

    // The inverse of fromBytes.
    private static ByteBuffer toBytes(CaveMapAnchors anchors) {
        ByteBuffer bytes = ByteBuffer.allocate(PIXELS * 2);
        bytes.asShortBuffer().put(anchors.heights);
        return bytes;
    }
}
