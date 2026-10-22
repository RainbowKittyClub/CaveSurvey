package ir.mehradn.cavesurvey.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * The static a vanilla map draws under a ceiling, as in the Nether: dirt or stone per pixel from
 * its position, never the terrain. Drawn by a cave map held in a dimension its kind does not
 * survey, in that dimension or any other.
 */
public final class CaveMapStatic {
    private static final int MAP_SIZE = 128;

    private CaveMapStatic() {
    }

    /**
     * Draws one tick of the static around the holder, as vanilla's {@code MapItem.update} does
     * under a ceiling: half its radius, one stripe of every 16 pixel columns per tick. Unlike
     * vanilla it reads only loaded chunks.
     */
    public static void update(ServerLevel level, Player player, MapItemSavedData data) {
        if (level.dimension() != data.dimension) {
            return;
        }
        int scale = 1 << data.scale;
        int centerX = data.centerX;
        int centerZ = data.centerZ;
        int playerImgX = Mth.floor(player.getX() - centerX) / scale + MAP_SIZE / 2;
        int playerImgY = Mth.floor(player.getZ() - centerZ) / scale + MAP_SIZE / 2;
        int radius = MAP_SIZE / scale / 2;
        MapItemSavedData.HoldingPlayer holder = data.getHoldingPlayer(player);
        holder.step++;
        boolean foundConsecutiveChanges = false;

        for (int imgX = playerImgX - radius + 1; imgX < playerImgX + radius; imgX++) {
            if ((imgX & 15) != (holder.step & 15) && !foundConsecutiveChanges) {
                continue;
            }
            foundConsecutiveChanges = false;
            double previousAverageAreaHeight = 0.0;
            for (int imgY = playerImgY - radius - 1; imgY < playerImgY + radius; imgY++) {
                if (imgX < 0 || imgY < -1 || imgX >= MAP_SIZE || imgY >= MAP_SIZE) {
                    continue;
                }
                int distanceToPlayerSqr = Mth.square(imgX - playerImgX)
                        + Mth.square(imgY - playerImgY);
                boolean ditherBlack = distanceToPlayerSqr > (radius - 2) * (radius - 2);
                int areaMinX = (centerX / scale + imgX - MAP_SIZE / 2) * scale;
                int areaMinZ = (centerZ / scale + imgY - MAP_SIZE / 2) * scale;
                LevelChunk chunk = level.getChunkSource().getChunkNow(
                        SectionPos.blockToSectionCoord(areaMinX),
                        SectionPos.blockToSectionCoord(areaMinZ));
                if (chunk == null || chunk.isEmpty()) {
                    continue;
                }
                MapColor color = staticColor(level, areaMinX, areaMinZ);
                double averageAreaHeight = 100.0;
                double diff = (averageAreaHeight - previousAverageAreaHeight) * 4.0 / (scale + 4)
                        + ((imgX + imgY & 1) - 0.5) * 0.4;
                MapColor.Brightness brightness;
                if (diff > 0.6) {
                    brightness = MapColor.Brightness.HIGH;
                } else if (diff < -0.6) {
                    brightness = MapColor.Brightness.LOW;
                } else {
                    brightness = MapColor.Brightness.NORMAL;
                }
                previousAverageAreaHeight = averageAreaHeight;
                if (imgY >= 0 && distanceToPlayerSqr < radius * radius
                        && (!ditherBlack || (imgX + imgY & 1) != 0)) {
                    foundConsecutiveChanges |= data.updateColor(imgX, imgY,
                            color.getPackedId(brightness));
                }
            }
        }
    }

    // Vanilla's ceiling noise: a hash of the area's corner picks dirt or stone.
    private static MapColor staticColor(ServerLevel level, int areaMinX, int areaMinZ) {
        int ceilingNoise = areaMinX + areaMinZ * 231871;
        ceilingNoise = ceilingNoise * ceilingNoise * 31287121 + ceilingNoise * 11;
        return ((ceilingNoise >> 20 & 1) == 0 ? Blocks.DIRT : Blocks.STONE).defaultBlockState()
                .getMapColor(level, BlockPos.ZERO);
    }
}
