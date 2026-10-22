package ir.mehradn.cavesurvey.util;

import java.util.Arrays;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

/**
 * Floods outward from a start position through connected air and records, per block column, the
 * floor that a map pixel should show.
 *
 * <p>The flood visits every air pocket connected to the start's. Each pocket climbs to its ceiling
 * and descends to its floor, then enters each neighbouring column once for every separate open run
 * beside it. Where a column has several reached pockets, the map's {@link PixelAnchors} pick one:
 * the pocket it showed before while that still exists, else the floor nearest the surveyor's feet.
 * A map that maps only under cover passes over pockets open to the sky while the column has a
 * covered one. Sections that hold only air are stepped over whole. The survey reads a
 * {@link Snapshot} taken on the server thread, so it can run on any thread; an unloaded chunk stops
 * the flood.
 */
public final class CaveMappingAlgorithm {
    private static final int[][] NEIGHBOURS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
    // A pixel whose columns average this much fluid already shades darkest (CaveMapDrawing).
    private static final int DARKEST_FLUID_DEPTH = 10;

    private final Snapshot snapshot;
    private final int minY;
    // One past the top block, the old exclusive getMaxBuildHeight(): the climb may end there.
    private final int topExclusive;
    // Deeper fluid is not counted: one column this deep keeps its pixel's average at the darkest.
    private final int maxFluidDepth;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    // Most reads hit the chunk of the read before, so it skips the map lookup.
    private long lastChunkKey;
    private PalettedContainer<BlockState> @Nullable [] lastChunk;

    private CaveMappingAlgorithm(Snapshot snapshot, int scaleShift) {
        this.snapshot = snapshot;
        this.minY = snapshot.getMinY();
        this.topExclusive = snapshot.getMinY() + snapshot.getHeight();
        this.maxFluidDepth = DARKEST_FLUID_DEPTH << 2 * scaleShift;
    }

    /**
     * Surveys every air pocket within {@code updateRadius} of the start that the flood reaches,
     * and picks one per column against the map's anchors.
     *
     * @param feetY the surveyor's feet height, which picks the pocket of a column with no anchor
     * @param coveredFirst whether a column's covered pockets win over those open to the sky
     */
    public static PixelMatrix run(BlockPos startPos, int updateRadius, int feetY,
            PixelAnchors anchors, Snapshot snapshot, boolean coveredFirst) {
        return new CaveMappingAlgorithm(snapshot, anchors.scaleShift()).flood(startPos,
                updateRadius, feetY, anchors, coveredFirst);
    }

    // Breadth-first over pockets; each queued entry is a height inside a pocket of its column.
    private PixelMatrix flood(BlockPos startPos, int updateRadius, int feetY,
            PixelAnchors anchors, boolean coveredFirst) {
        PixelMatrix matrix = new PixelMatrix(updateRadius, startPos.getX(), startPos.getZ(),
                this.topExclusive, coveredFirst);
        this.queue.enqueue(startPos.asLong());
        while (!this.queue.isEmpty()) {
            long entry = this.queue.dequeueLong();
            int x = BlockPos.getX(entry);
            int z = BlockPos.getZ(entry);
            int entryY = BlockPos.getY(entry);
            // An entry inside a pocket already surveyed would climb and descend to that pocket.
            if (matrix.holds(x, z, entryY)) {
                continue;
            }
            int ceiling = climb(x, entryY, z);
            int floor = descend(x, ceiling, z);
            matrix.addPocket(x, z, floor, ceiling);
            int bottom = Math.min(floor + 1, ceiling);
            for (int[] neighbour : NEIGHBOURS) {
                enterOpenRuns(x + neighbour[0], z + neighbour[1], ceiling, bottom, matrix);
            }
        }
        matrix.choose(this, anchors, feetY);
        return matrix;
    }

    // Climbs from y to the top of its run of colourless blocks: the pocket's ceiling.
    private int climb(int x, int startY, int z) {
        int y = startY;
        while (y < this.topExclusive) {
            int airTop = airRunTop(x, y + 1, z);
            if (airTop >= y + 1) {
                y = airTop;
            } else if (colorless(x, y + 1, z)) {
                y++;
            } else {
                break;
            }
        }
        return y;
    }

    // Descends from the ceiling to the first block with a map colour: the pocket's floor.
    private int descend(int x, int ceiling, int z) {
        int y = ceiling;
        while (y > this.minY) {
            int airBottom = airRunBottom(x, y - 1, z);
            if (airBottom <= y - 1) {
                y = airBottom;
                continue;
            }
            y--;
            if (!colorless(x, y, z)) {
                break;
            }
        }
        return y;
    }

    // Queues one entry for each separate open run of column (x, z) between top and bottom. A run
    // the flood already surveyed as a pocket is passed over whole, down to that pocket's floor.
    private void enterOpenRuns(int x, int z, int top, int bottom, PixelMatrix matrix) {
        int cell = matrix.index(x, z);
        if (cell < 0) {
            return;
        }
        boolean open = false;
        int y = top;
        while (y >= bottom) {
            int airBottom = airRunBottom(x, y, z);
            boolean inAir = airBottom <= y;
            boolean nowOpen = inAir || colorless(x, y, z);
            if (nowOpen && !open) {
                int surveyed = matrix.holding(cell, y);
                if (surveyed >= 0) {
                    y = matrix.pocketFloors.getInt(surveyed) - 1;
                    continue;
                }
                this.queue.enqueue(BlockPos.asLong(x, y, z));
            }
            open = nowOpen;
            y = inAir ? Math.max(airBottom, bottom) - 1 : y - 1;
        }
    }

    // A block lets the flood through when it has no map colour, as air does. The two lowest
    // layers always read as bedrock, and an unloaded chunk blocks the flood.
    private boolean colorless(int x, int y, int z) {
        if (y <= this.minY + 1) {
            return false;
        }
        PalettedContainer<BlockState>[] chunk = chunkAt(x, z);
        if (chunk == Snapshot.UNLOADED) {
            return false;
        }
        return mapColor(this.snapshot.stateIn(chunk, x, y, z), x, y, z) == MapColor.NONE;
    }

    // The top y of the all-air section holding (x, y, z), or y - 1 when that section is not all air
    // or y is in the bedrock layers. Above the world, the world's top.
    private int airRunTop(int x, int y, int z) {
        if (y >= this.topExclusive) {
            return this.topExclusive;
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
        PalettedContainer<BlockState>[] chunk = chunkAt(x, z);
        return chunk != Snapshot.UNLOADED && this.snapshot.sectionIsAir(chunk, y);
    }

    // The copied sections of the chunk holding column (x, z), or Snapshot.UNLOADED.
    private PalettedContainer<BlockState>[] chunkAt(int x, int z) {
        long key = ChunkPos.pack(SectionPos.blockToSectionCoord(x),
                SectionPos.blockToSectionCoord(z));
        if (this.lastChunk != null && key == this.lastChunkKey) {
            return this.lastChunk;
        }
        PalettedContainer<BlockState>[] chunk = this.snapshot.chunk(key);
        this.lastChunkKey = key;
        this.lastChunk = chunk;
        return chunk;
    }

    // The block's map colour; the snapshot and position go along for BlockState's signature.
    private MapColor mapColor(BlockState state, int x, int y, int z) {
        return state.getMapColor(this.snapshot, this.cursor.set(x, y, z));
    }

    // What the floor at (x, floorY, z) shows: its colour and the depth of any fluid on it.
    private void describeFloor(int x, int floorY, int z, int[] out) {
        if (floorY <= this.minY + 1) {
            out[0] = mapColor(Blocks.BEDROCK.defaultBlockState(), x, floorY, z).id;
            out[1] = 0;
            return;
        }
        PalettedContainer<BlockState>[] chunk = chunkAt(x, z);
        if (chunk == Snapshot.UNLOADED) {
            out[0] = -1;
            return;
        }
        BlockState state = this.snapshot.stateIn(chunk, x, floorY, z);
        FluidState fluid = state.getFluidState();
        if (fluid.isEmpty()) {
            out[0] = mapColor(state, x, floorY, z).id;
            out[1] = 0;
            return;
        }
        // A fluid in a block without a sturdy top shows as the fluid, as on a vanilla map.
        this.cursor.set(x, floorY, z);
        BlockState shown = state.isFaceSturdy(this.snapshot, this.cursor, Direction.UP)
                ? state : fluid.createLegacyBlock();
        out[0] = mapColor(shown, x, floorY, z).id;
        int depth = 0;
        int y = floorY;
        while (y > this.minY && depth < this.maxFluidDepth
                && !this.snapshot.stateIn(chunk, x, y, z).getFluidState().isEmpty()) {
            y--;
            depth++;
        }
        out[1] = depth;
    }

    /**
     * Copies of the block states a survey can read, taken on the server thread so the survey can
     * run on another. Also the {@link BlockGetter} the survey's colour and support checks read.
     */
    public static final class Snapshot implements BlockGetter {
        // A chunk that was not loaded when the snapshot was taken.
        @SuppressWarnings("unchecked")
        private static final PalettedContainer<BlockState>[] UNLOADED = new PalettedContainer[0];

        private final Long2ObjectOpenHashMap<PalettedContainer<BlockState>[]> chunks =
                new Long2ObjectOpenHashMap<>();
        private final int minY;
        private final int height;
        private final int minSectionY;

        private Snapshot(int minY, int height) {
            this.minY = minY;
            this.height = height;
            this.minSectionY = SectionPos.blockToSectionCoord(minY);
        }

        /**
         * Copies every loaded chunk a survey of {@code radius} around column (x, z) can read, from
         * one block beyond the square on each side. Server thread only.
         */
        public static Snapshot take(Level level, int centerX, int centerZ, int radius) {
            Snapshot snapshot = new Snapshot(level.getMinY(), level.getHeight());
            int minChunkX = SectionPos.blockToSectionCoord(centerX - radius - 1);
            int maxChunkX = SectionPos.blockToSectionCoord(centerX + radius);
            int minChunkZ = SectionPos.blockToSectionCoord(centerZ - radius - 1);
            int maxChunkZ = SectionPos.blockToSectionCoord(centerZ + radius);
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    snapshot.chunks.put(ChunkPos.pack(chunkX, chunkZ),
                            chunk == null ? UNLOADED : copyStates(chunk.getSections()));
                }
            }
            return snapshot;
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            PalettedContainer<BlockState>[] chunk = chunk(ChunkPos.pack(
                    SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ())));
            if (chunk == UNLOADED) {
                return Blocks.AIR.defaultBlockState();
            }
            return stateIn(chunk, pos.getX(), pos.getY(), pos.getZ());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return this.height;
        }

        @Override
        public int getMinY() {
            return this.minY;
        }

        // Each section's block states, copied; an all-air section is null.
        @SuppressWarnings("unchecked")
        private static PalettedContainer<BlockState>[] copyStates(LevelChunkSection[] sections) {
            PalettedContainer<BlockState>[] copy = new PalettedContainer[sections.length];
            for (int i = 0; i < sections.length; i++) {
                copy[i] = sections[i].hasOnlyAir() ? null : sections[i].getStates().copy();
            }
            return copy;
        }

        // The copied sections of a chunk by ChunkPos.pack key, or UNLOADED outside the snapshot.
        private PalettedContainer<BlockState>[] chunk(long key) {
            PalettedContainer<BlockState>[] chunk = this.chunks.get(key);
            return chunk == null ? UNLOADED : chunk;
        }

        // The copied section at height y of a loaded chunk; null if all air or off the world.
        private @Nullable PalettedContainer<BlockState> section(
                PalettedContainer<BlockState>[] chunk, int y) {
            int index = SectionPos.blockToSectionCoord(y) - this.minSectionY;
            return index < 0 || index >= chunk.length ? null : chunk[index];
        }

        // Whether the section at height y of a loaded chunk holds only air (or lies off the world).
        private boolean sectionIsAir(PalettedContainer<BlockState>[] chunk, int y) {
            return section(chunk, y) == null;
        }

        // The block at (x, y, z) in a loaded chunk, as LevelChunk.getBlockState reads it.
        private BlockState stateIn(PalettedContainer<BlockState>[] chunk, int x, int y, int z) {
            PalettedContainer<BlockState> section = section(chunk, y);
            return section == null ? Blocks.AIR.defaultBlockState()
                    : section.get(x & 15, y & 15, z & 15);
        }
    }

    /** The surveyed columns, a square of side {@code 2 * size} centred on the start. */
    public static final class PixelMatrix {
        private final int size;
        private final int centerX;
        private final int centerZ;
        private final int side;
        // A pocket whose ceiling reaches this is open to the sky; at or above, since a survey
        // from above the build limit starts above the world top.
        private final int topExclusive;
        // Whether covered pockets win over open ones, which then show the ceiling static.
        private final boolean coveredFirst;
        // Each column's reached pockets, as a linked list through the three lists below.
        private final int[] firstPocket;
        private final IntArrayList pocketFloors = new IntArrayList();
        private final IntArrayList pocketCeilings = new IntArrayList();
        private final IntArrayList nextPocket = new IntArrayList();
        private final boolean[] reached;
        private final byte[] colors;
        private final int[] floors;
        private final int[] fluidDepths;
        private final boolean[] skies;

        private PixelMatrix(int halfSize, int centerX, int centerZ, int topExclusive,
                boolean coveredFirst) {
            this.size = halfSize;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.side = halfSize * 2;
            this.topExclusive = topExclusive;
            this.coveredFirst = coveredFirst;
            int cells = this.side * this.side;
            this.firstPocket = new int[cells];
            Arrays.fill(this.firstPocket, -1);
            this.reached = new boolean[cells];
            this.colors = new byte[cells];
            this.floors = new int[cells];
            this.fluidDepths = new int[cells];
            this.skies = new boolean[cells];
        }

        /** Returns whether the column shows a pocket, chosen from those the flood reached. */
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

        /** Returns whether a reached column shows the ceiling static: it is open, covered first. */
        public boolean showsStatic(int x, int z) {
            return this.coveredFirst && this.skies[index(x, z)];
        }

        // Whether a pocket already recorded in the column holds height y.
        private boolean holds(int x, int z, int y) {
            int i = index(x, z);
            return i >= 0 && holding(i, y) >= 0;
        }

        // Records a reached pocket of the column.
        private void addPocket(int x, int z, int floor, int ceiling) {
            int i = index(x, z);
            this.pocketFloors.add(floor);
            this.pocketCeilings.add(ceiling);
            this.nextPocket.add(this.firstPocket[i]);
            this.firstPocket[i] = this.pocketFloors.size() - 1;
        }

        // Picks each column's pocket: the one holding its anchor height; none while the anchor is
        // still open but unreached; else the floor nearest the anchor, or the feet with no anchor.
        // With coveredFirst, a pocket open to the sky only when the column has no covered one.
        private void choose(CaveMappingAlgorithm scan, PixelAnchors anchors, int feetY) {
            int[] floor = new int[2];
            for (int i = 0; i < this.firstPocket.length; i++) {
                if (this.firstPocket[i] < 0) {
                    continue;
                }
                int x = this.centerX - this.size + i / this.side;
                int z = this.centerZ - this.size + i % this.side;
                int anchor = anchors.atColumn(x, z);
                int chosen;
                if (anchor == PixelAnchors.NONE) {
                    chosen = nearest(i, feetY, this.coveredFirst);
                } else {
                    chosen = holding(i, anchor);
                    if (chosen < 0 && scan.colorless(x, anchor, z)) {
                        continue;
                    }
                    if (chosen < 0 || this.coveredFirst && open(chosen)) {
                        chosen = nearest(i, anchor, this.coveredFirst);
                    }
                }
                int floorY = this.pocketFloors.getInt(chosen);
                scan.describeFloor(x, floorY, z, floor);
                if (floor[0] < 0) {
                    continue;
                }
                this.reached[i] = true;
                this.colors[i] = (byte) floor[0];
                this.floors[i] = floorY;
                this.fluidDepths[i] = floor[1];
                this.skies[i] = open(chosen);
            }
        }

        // Whether pocket p climbs to the top of the world, open to the sky.
        private boolean open(int p) {
            return this.pocketCeilings.getInt(p) >= this.topExclusive;
        }

        // The reached pocket of cell i whose open blocks include height, or -1.
        private int holding(int i, int height) {
            for (int p = this.firstPocket[i]; p >= 0; p = this.nextPocket.getInt(p)) {
                if (this.pocketFloors.getInt(p) < height
                        && height <= this.pocketCeilings.getInt(p)) {
                    return p;
                }
            }
            return -1;
        }

        // The reached pocket of cell i whose standing height is nearest height, the lower on a tie;
        // with skipOpen, one open to the sky only when no other pocket was reached.
        private int nearest(int i, int height, boolean skipOpen) {
            int best = -1;
            int bestDistance = Integer.MAX_VALUE;
            for (int p = this.firstPocket[i]; p >= 0; p = this.nextPocket.getInt(p)) {
                if (skipOpen && open(p)) {
                    continue;
                }
                int floor = this.pocketFloors.getInt(p);
                int distance = Math.abs(floor + 1 - height);
                if (distance < bestDistance
                        || distance == bestDistance && floor < this.pocketFloors.getInt(best)) {
                    best = p;
                    bestDistance = distance;
                }
            }
            if (best < 0 && skipOpen) {
                return nearest(i, height, false);
            }
            return best;
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
