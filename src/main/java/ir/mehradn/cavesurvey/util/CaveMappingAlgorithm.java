package ir.mehradn.cavesurvey.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Queue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

/**
 * Floods outward from a start position through connected air and records, per block column, the
 * floor that a map pixel should show.
 */
public final class CaveMappingAlgorithm {
    private CaveMappingAlgorithm() {
    }

    /** Surveys every column within {@code updateRadius} of the start that the flood reaches. */
    public static PixelMatrix run(BlockPos startPos, int updateRadius, Level level) {
        PixelMatrix matrix = new PixelMatrix(updateRadius, startPos.getX(), startPos.getZ());
        Queue<BlockPos> updates = new ArrayDeque<>();
        updates.add(startPos);

        while (!updates.isEmpty()) {
            BlockPos pos = updates.remove();
            if (matrix.outOfRange(pos) || matrix.get(pos) != null) {
                continue;
            }
            matrix.set(pos, update(pos, updates, level));
        }
        return matrix;
    }

    // Surveys one column and queues the neighbouring columns its air connects to.
    private static PixelInfo update(BlockPos pos, Queue<BlockPos> updates, Level level) {
        ColorGrabber colorGrabber = new ColorGrabber(level, pos);
        CloudCalculations cloud = new CloudCalculations(pos, colorGrabber);

        if (cloud.north != null) {
            updates.add(cloud.north);
        }
        if (cloud.south != null) {
            updates.add(cloud.south);
        }
        if (cloud.west != null) {
            updates.add(cloud.west);
        }
        if (cloud.east != null) {
            updates.add(cloud.east);
        }
        return cloud.pixelInfo;
    }

    // One past the top block, the old exclusive getMaxBuildHeight(): the climb may end there.
    private static int topExclusive(Level level) {
        return level.getMaxY() + 1;
    }

    /**
     * What one surveyed column shows.
     *
     * @param color the map colour of the column's floor
     * @param y the floor's height
     * @param fluidDepth how many fluid blocks stand on the floor, 0 for a dry floor
     * @param reachesSky whether the column is open to the top of the world
     */
    public record PixelInfo(MapColor color, int y, int fluidDepth, boolean reachesSky) {
    }

    /** The surveyed columns, a square of side {@code 2 * size} centred on the start. */
    public static final class PixelMatrix {
        /** Half the side of the square, in blocks. */
        public final int size;
        /** The x of the square's centre. */
        public final int centerX;
        /** The z of the square's centre. */
        public final int centerZ;
        /** The columns, indexed by offset from the square's low corner. */
        public PixelInfo[][] matrix;

        /** Creates an empty square of side {@code 2 * halfSize} centred on the given column. */
        public PixelMatrix(int halfSize, int centerX, int centerZ) {
            this.size = halfSize;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.matrix = new PixelInfo[halfSize * 2][halfSize * 2];
        }

        /** Returns whether the column lies outside the square. */
        public boolean outOfRange(int x, int z) {
            return this.centerX - this.size > x || x >= this.centerX + this.size
                    || this.centerZ - this.size > z || z >= this.centerZ + this.size;
        }

        /** Returns whether the position's column lies outside the square. */
        public boolean outOfRange(BlockPos pos) {
            return outOfRange(pos.getX(), pos.getZ());
        }

        /** Returns the column's survey, or null when it is outside or was never reached. */
        public PixelInfo get(int x, int z) {
            if (outOfRange(x, z)) {
                return null;
            }
            return this.matrix[x - this.centerX + this.size][z - this.centerZ + this.size];
        }

        /** Returns the survey of the position's column, or null. */
        public PixelInfo get(BlockPos pos) {
            return get(pos.getX(), pos.getZ());
        }

        /** Stores the survey of the position's column, ignoring columns outside the square. */
        public void set(BlockPos pos, PixelInfo info) {
            if (outOfRange(pos)) {
                return;
            }
            int offsetX = pos.getX() - this.centerX + this.size;
            int offsetZ = pos.getZ() - this.centerZ + this.size;
            this.matrix[offsetX][offsetZ] = info;
        }
    }

    /** Reads map colours around one column from that column's chunk and its edge neighbours. */
    public static final class ColorGrabber {
        /** The level being surveyed. */
        public final Level level;
        private final ArrayList<LevelChunk> chunks;

        /** Loads the column's chunk, and the neighbour across any chunk edge the column touches. */
        public ColorGrabber(Level level, BlockPos pos) {
            this.level = level;

            ChunkPos chunkPos = new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
            this.chunks = new ArrayList<>(
                    Collections.singletonList(level.getChunk(chunkPos.x(), chunkPos.z())));

            int innerX = pos.getX() & 15;
            int innerZ = pos.getZ() & 15;
            if (innerX == 0) {
                this.chunks.add(level.getChunk(chunkPos.x() - 1, chunkPos.z()));
            }
            if (innerX == 15) {
                this.chunks.add(level.getChunk(chunkPos.x() + 1, chunkPos.z()));
            }
            if (innerZ == 0) {
                this.chunks.add(level.getChunk(chunkPos.x(), chunkPos.z() - 1));
            }
            if (innerZ == 15) {
                this.chunks.add(level.getChunk(chunkPos.x(), chunkPos.z() + 1));
            }
        }

        /** Returns the block's map colour, or null when its chunk was not loaded here. */
        public MapColor getColor(BlockPos pos) {
            if (pos.getY() <= this.level.getMinY() + 1) {
                return Blocks.BEDROCK.defaultBlockState().getMapColor(this.level, pos);
            }

            ChunkPos chunkPos = new ChunkPos(SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ()));
            for (LevelChunk chunk : this.chunks) {
                if (chunk.getPos().equals(chunkPos)) {
                    return chunk.getBlockState(pos).getMapColor(this.level, pos);
                }
            }
            return null;
        }

        /** Returns what a column with this floor and ceiling shows, or null off its chunks. */
        public PixelInfo getPixelInfo(BlockPos pos, int ceiling) {
            boolean reachesSky = ceiling == topExclusive(this.level);
            if (pos.getY() <= this.level.getMinY() + 1) {
                MapColor bedrock = Blocks.BEDROCK.defaultBlockState().getMapColor(this.level, pos);
                return new PixelInfo(bedrock, pos.getY(), 0, reachesSky);
            }

            ChunkPos chunkPos = new ChunkPos(SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ()));
            for (LevelChunk chunk : this.chunks) {
                if (!chunk.getPos().equals(chunkPos)) {
                    continue;
                }
                BlockState state = chunk.getBlockState(pos);

                MapColor color;
                int fluidDepth = 0;
                if (state.getFluidState().isEmpty()) {
                    color = state.getMapColor(this.level, pos);
                } else {
                    color = getCorrectStateForFluidBlock(this.level, state, pos)
                            .getMapColor(this.level, pos);
                    BlockPos fluidPos = pos;
                    while (fluidPos.getY() > this.level.getMinY()
                            && !chunk.getBlockState(fluidPos).getFluidState().isEmpty()) {
                        fluidPos = fluidPos.below();
                        fluidDepth++;
                    }
                }
                return new PixelInfo(color, pos.getY(), fluidDepth, reachesSky);
            }
            return null;
        }

        // A fluid in a block without a sturdy top shows as the fluid, as on a vanilla map.
        private static BlockState getCorrectStateForFluidBlock(Level level, BlockState state,
                BlockPos pos) {
            FluidState fluidState = state.getFluidState();
            if (!fluidState.isEmpty() && !state.isFaceSturdy(level, pos, Direction.UP)) {
                return fluidState.createLegacyBlock();
            }
            return state;
        }
    }

    /**
     * One column's survey: climbs to the ceiling, then descends to the floor, noting each
     * neighbouring column whose air connects to this one.
     */
    public static final class CloudCalculations {
        /** The first connected position found in the column to the north, or null. */
        public BlockPos north;
        /** The first connected position found in the column to the south, or null. */
        public BlockPos south;
        /** The first connected position found in the column to the west, or null. */
        public BlockPos west;
        /** The first connected position found in the column to the east, or null. */
        public BlockPos east;
        /** What the column shows, or null when it ran off the loaded chunks. */
        public PixelInfo pixelInfo;

        /** Surveys the column through the given position. */
        public CloudCalculations(BlockPos initialPosition, ColorGrabber colorGrabber) {
            if (initialPosition == null) {
                return;
            }
            BlockPos pos = initialPosition;
            int top = topExclusive(colorGrabber.level);

            while (pos.getY() < top && colorless(pos.above(), colorGrabber)) {
                pos = pos.above();
            }
            int ceiling = pos.getY();

            do {
                if (this.north == null && colorless(pos.north(), colorGrabber)) {
                    this.north = pos.north();
                }
                if (this.south == null && colorless(pos.south(), colorGrabber)) {
                    this.south = pos.south();
                }
                if (this.west == null && colorless(pos.west(), colorGrabber)) {
                    this.west = pos.west();
                }
                if (this.east == null && colorless(pos.east(), colorGrabber)) {
                    this.east = pos.east();
                }

                if (pos.getY() <= colorGrabber.level.getMinY()) {
                    break;
                }
                pos = pos.below();
            } while (colorless(pos, colorGrabber));

            this.pixelInfo = colorGrabber.getPixelInfo(pos, ceiling);
        }

        // A block with no map colour, such as air, lets the survey pass through it.
        private static boolean colorless(BlockPos pos, ColorGrabber colorGrabber) {
            return colorGrabber.getColor(pos) == MapColor.NONE;
        }
    }
}
