package ir.mehradn.cavesurvey.util;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

/**
 * Floods outward from a start position through connected air and records, per block column, the
 * floor that a map pixel should show.
 *
 * <p>Each column climbs from where the flood entered it to its ceiling, then descends to its
 * floor, noting on the way down the first height at which each neighbouring column is open too.
 * Sections that hold only air are stepped over whole. Chunks are read only when already loaded:
 * the flood stops at an unloaded chunk instead of loading it.
 */
public final class CaveMappingAlgorithm {
    private static final LevelChunkSection[] UNLOADED = new LevelChunkSection[0];
    private static final int[][] NEIGHBOURS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    private final Level level;
    private final int minY;
    // One past the top block, the old exclusive getMaxBuildHeight(): the climb may end there.
    private final int topExclusive;
    private final int minSectionY;
    private final Long2ObjectOpenHashMap<LevelChunkSection[]> sections =
            new Long2ObjectOpenHashMap<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final int[] neighbourY = new int[NEIGHBOURS.length];
    // Most reads hit the chunk of the read before, so it skips the map lookup.
    private boolean hasLastChunk;
    private long lastChunkKey;
    private LevelChunkSection[] lastChunk;

    private CaveMappingAlgorithm(Level level) {
        this.level = level;
        this.minY = level.getMinY();
        this.topExclusive = level.getMaxY() + 1;
        this.minSectionY = level.getMinSectionY();
    }

    /** Surveys every column within {@code updateRadius} of the start that the flood reaches. */
    public static PixelMatrix run(BlockPos startPos, int updateRadius, Level level) {
        return new CaveMappingAlgorithm(level).flood(startPos, updateRadius);
    }

    // Breadth-first over columns; a column is surveyed from the height the flood first entered it.
    private PixelMatrix flood(BlockPos startPos, int updateRadius) {
        PixelMatrix matrix = new PixelMatrix(updateRadius, startPos.getX(), startPos.getZ());
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        matrix.markQueued(startPos.getX(), startPos.getZ());
        queue.enqueue(startPos.asLong());

        while (!queue.isEmpty()) {
            long packed = queue.dequeueLong();
            int x = BlockPos.getX(packed);
            int z = BlockPos.getZ(packed);
            surveyColumn(x, BlockPos.getY(packed), z, matrix);
            for (int d = 0; d < NEIGHBOURS.length; d++) {
                int y = this.neighbourY[d];
                int nx = x + NEIGHBOURS[d][0];
                int nz = z + NEIGHBOURS[d][1];
                if (y != Integer.MIN_VALUE && matrix.markQueued(nx, nz)) {
                    queue.enqueue(BlockPos.asLong(nx, y, nz));
                }
            }
        }
        return matrix;
    }

    // Surveys one column into the matrix and leaves each neighbour's entry height in neighbourY.
    private void surveyColumn(int x, int startY, int z, PixelMatrix matrix) {
        int y = startY;
        while (y < this.topExclusive) {
            int airTop = airRunTop(x, y + 1, z);
            if (airTop >= y + 1) {
                y = Math.min(airTop, this.topExclusive);
            } else if (colorless(x, y + 1, z)) {
                y++;
            } else {
                break;
            }
        }
        int ceiling = y;

        int pending = NEIGHBOURS.length;
        for (int d = 0; d < NEIGHBOURS.length; d++) {
            this.neighbourY[d] = Integer.MIN_VALUE;
        }
        while (true) {
            for (int d = 0; d < NEIGHBOURS.length && pending > 0; d++) {
                if (this.neighbourY[d] == Integer.MIN_VALUE
                        && colorless(x + NEIGHBOURS[d][0], y, z + NEIGHBOURS[d][1])) {
                    this.neighbourY[d] = y;
                    pending--;
                }
            }
            if (y <= this.minY) {
                break;
            }
            if (pending == 0) {
                int airBottom = airRunBottom(x, y - 1, z);
                if (airBottom <= y - 1) {
                    y = airBottom;
                    continue;
                }
            }
            if (!colorless(x, y - 1, z)) {
                y--;
                break;
            }
            y--;
        }
        matrix.set(x, z, this, y, ceiling);
    }

    // A block lets the flood through when it has no map colour, as air does. The two lowest
    // layers always read as bedrock, and an unloaded chunk blocks the flood.
    private boolean colorless(int x, int y, int z) {
        if (y <= this.minY + 1) {
            return false;
        }
        LevelChunkSection[] chunk = sectionsAt(x, z);
        if (chunk == UNLOADED) {
            return false;
        }
        return mapColor(stateIn(chunk, x, y, z), x, y, z) == MapColor.NONE;
    }

    // The top y of the all-air section holding (x, y, z), or y - 1 when that section is not all air
    // or y is in the bedrock layers. Above the world counts as air without limit.
    private int airRunTop(int x, int y, int z) {
        if (y >= this.topExclusive) {
            return Integer.MAX_VALUE;
        }
        if (y <= this.minY + 1 || !sectionIsAir(x, y, z)) {
            return y - 1;
        }
        return SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(y)) + 15;
    }

    // The bottom y of the all-air run below the column through (x, y, z), clamped above the
    // bedrock layers, or y + 1 when (x, y, z) is not in an all-air section.
    private int airRunBottom(int x, int y, int z) {
        if (y <= this.minY + 1 || !sectionIsAir(x, y, z)) {
            return y + 1;
        }
        int sectionBottom = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(y));
        return Math.max(sectionBottom, this.minY + 2);
    }

    // Whether the section holding (x, y, z) is loaded and holds only air.
    private boolean sectionIsAir(int x, int y, int z) {
        LevelChunkSection[] chunk = sectionsAt(x, z);
        if (chunk == UNLOADED) {
            return false;
        }
        int index = SectionPos.blockToSectionCoord(y) - this.minSectionY;
        return index < 0 || index >= chunk.length || chunk[index].hasOnlyAir();
    }

    // The sections of the chunk holding column (x, z), or UNLOADED; cached for the whole scan.
    private LevelChunkSection[] sectionsAt(int x, int z) {
        int chunkX = SectionPos.blockToSectionCoord(x);
        int chunkZ = SectionPos.blockToSectionCoord(z);
        long key = ChunkPos.pack(chunkX, chunkZ);
        if (this.hasLastChunk && key == this.lastChunkKey) {
            return this.lastChunk;
        }
        LevelChunkSection[] chunk = this.sections.get(key);
        if (chunk == null) {
            LevelChunk loaded = this.level.getChunkSource().getChunkNow(chunkX, chunkZ);
            chunk = loaded == null ? UNLOADED : loaded.getSections();
            this.sections.put(key, chunk);
        }
        this.hasLastChunk = true;
        this.lastChunkKey = key;
        this.lastChunk = chunk;
        return chunk;
    }

    // The block at (x, y, z) in a loaded chunk's sections, as LevelChunk.getBlockState reads it.
    private BlockState stateIn(LevelChunkSection[] chunk, int x, int y, int z) {
        int index = SectionPos.blockToSectionCoord(y) - this.minSectionY;
        if (index < 0 || index >= chunk.length || chunk[index].hasOnlyAir()) {
            return Blocks.AIR.defaultBlockState();
        }
        return chunk[index].getBlockState(x & 15, y & 15, z & 15);
    }

    // The block's map colour; level and position go along for parity with BlockState's signature.
    private MapColor mapColor(BlockState state, int x, int y, int z) {
        return state.getMapColor(this.level, this.cursor.set(x, y, z));
    }

    // What the floor at (x, floorY, z) shows: its colour and the depth of any fluid on it.
    private void describeFloor(int x, int floorY, int z, int[] out) {
        if (floorY <= this.minY + 1) {
            out[0] = mapColor(Blocks.BEDROCK.defaultBlockState(), x, floorY, z).id;
            out[1] = 0;
            return;
        }
        LevelChunkSection[] chunk = sectionsAt(x, z);
        if (chunk == UNLOADED) {
            out[0] = -1;
            return;
        }
        BlockState state = stateIn(chunk, x, floorY, z);
        FluidState fluid = state.getFluidState();
        if (fluid.isEmpty()) {
            out[0] = mapColor(state, x, floorY, z).id;
            out[1] = 0;
            return;
        }
        // A fluid in a block without a sturdy top shows as the fluid, as on a vanilla map.
        this.cursor.set(x, floorY, z);
        BlockState shown = state.isFaceSturdy(this.level, this.cursor, Direction.UP)
                ? state : fluid.createLegacyBlock();
        out[0] = mapColor(shown, x, floorY, z).id;
        int depth = 0;
        int y = floorY;
        while (y > this.minY && !stateIn(chunk, x, y, z).getFluidState().isEmpty()) {
            y--;
            depth++;
        }
        out[1] = depth;
    }

    /** The surveyed columns, a square of side {@code 2 * size} centred on the start. */
    public static final class PixelMatrix {
        private final int size;
        private final int centerX;
        private final int centerZ;
        private final int side;
        private final boolean[] queued;
        private final boolean[] reached;
        private final byte[] colors;
        private final int[] floors;
        private final int[] fluidDepths;
        private final boolean[] skies;
        private final int[] floorScratch = new int[2];

        private PixelMatrix(int halfSize, int centerX, int centerZ) {
            this.size = halfSize;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.side = halfSize * 2;
            int cells = this.side * this.side;
            this.queued = new boolean[cells];
            this.reached = new boolean[cells];
            this.colors = new byte[cells];
            this.floors = new int[cells];
            this.fluidDepths = new int[cells];
            this.skies = new boolean[cells];
        }

        /** Returns whether the flood reached the column and found what it shows. */
        public boolean reached(int x, int z) {
            int i = index(x, z);
            return i >= 0 && this.reached[i];
        }

        /** Returns the {@link MapColor} id of a reached column's floor. */
        public int colorId(int x, int z) {
            return this.colors[index(x, z)] & 0xff;
        }

        /** Returns the height of a reached column's floor. */
        public int floorY(int x, int z) {
            return this.floors[index(x, z)];
        }

        /** Returns how many fluid blocks stand on a reached column's floor. */
        public int fluidDepth(int x, int z) {
            return this.fluidDepths[index(x, z)];
        }

        /** Returns whether a reached column is open to the top of the world. */
        public boolean reachesSky(int x, int z) {
            return this.skies[index(x, z)];
        }

        // Marks the column queued; false when it is outside the square or was already queued.
        private boolean markQueued(int x, int z) {
            int i = index(x, z);
            if (i < 0 || this.queued[i]) {
                return false;
            }
            this.queued[i] = true;
            return true;
        }

        // Records what the column shows, or nothing when its floor lies in an unloaded chunk.
        private void set(int x, int z, CaveMappingAlgorithm scan, int floorY, int ceiling) {
            int i = index(x, z);
            if (i < 0) {
                return;
            }
            scan.describeFloor(x, floorY, z, this.floorScratch);
            if (this.floorScratch[0] < 0) {
                return;
            }
            this.reached[i] = true;
            this.colors[i] = (byte) this.floorScratch[0];
            this.floors[i] = floorY;
            this.fluidDepths[i] = this.floorScratch[1];
            this.skies[i] = ceiling == scan.topExclusive;
        }

        // The column's index, or -1 outside the square.
        private int index(int x, int z) {
            int offsetX = x - this.centerX + this.size;
            int offsetZ = z - this.centerZ + this.size;
            if (offsetX < 0 || offsetX >= this.side || offsetZ < 0 || offsetZ >= this.side) {
                return -1;
            }
            return offsetX * this.side + offsetZ;
        }
    }
}
