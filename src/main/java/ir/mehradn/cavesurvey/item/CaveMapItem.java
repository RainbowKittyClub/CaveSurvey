package ir.mehradn.cavesurvey.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * A filled cave map: each use surveys the cave around the player into the map, and a map held in
 * either hand surveys again about once a second. In a dimension its kind does not survey, it
 * behaves as a vanilla map does in the Nether.
 */
public class CaveMapItem extends MapItem implements PolymerItem {
    private static final int BANNER_BLOCK_RADIUS = 64;
    // Ticks a held cave map waits between surveys of its own.
    private static final int HELD_SURVEY_TICKS = 20;

    private final CaveMapKind kind;

    /** Creates a filled map of {@code kind}. */
    public CaveMapItem(CaveMapKind kind, Item.Properties properties) {
        super(properties);
        this.kind = kind;
    }

    /** Creates a filled map of {@code item} at scale 0 centred on the given block column. */
    public static ItemStack create(ServerLevel level, int x, int z, Item item) {
        ItemStack stack = new ItemStack(item);
        MapItem.applyNewSavedData(level, stack, x, z, (byte) 0, true, false);
        return stack;
    }

    /** Returns where this map surveys. */
    public CaveMapKind kind() {
        return this.kind;
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
        int relativeX = (headPos.getX() - centerX) / scale + IMAGE_WIDTH / 2;
        int relativeZ = (headPos.getZ() - centerZ) / scale + IMAGE_WIDTH / 2;
        int pixelRadius = BANNER_BLOCK_RADIUS / scale;

        if (IMAGE_WIDTH + pixelRadius + 4 < relativeX || relativeX < -pixelRadius - 4
                || IMAGE_WIDTH + pixelRadius + 4 < relativeZ || relativeZ < -pixelRadius - 4) {
            return;
        }
        // The square of pixels near the viewer, clipped to the map, as blocks.
        int minPixelX = Math.max(relativeX - pixelRadius, 0);
        int maxPixelX = Math.min(relativeX + pixelRadius - 1, IMAGE_WIDTH - 1);
        int minPixelZ = Math.max(relativeZ - pixelRadius, 0);
        int maxPixelZ = Math.min(relativeZ + pixelRadius - 1, IMAGE_WIDTH - 1);
        if (minPixelX > maxPixelX || minPixelZ > maxPixelZ) {
            return;
        }
        int minX = (centerX / scale + minPixelX - IMAGE_WIDTH / 2) * scale;
        int maxX = (centerX / scale + maxPixelX - IMAGE_WIDTH / 2) * scale + scale - 1;
        int minZ = (centerZ / scale + minPixelZ - IMAGE_WIDTH / 2) * scale;
        int maxZ = (centerZ / scale + maxPixelZ - IMAGE_WIDTH / 2) * scale + scale - 1;

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
    public Identifier getPolymerItemModel(ItemStack itemStack, PacketContext context,
            HolderLookup.Provider lookup) {
        return CaveMapItems.clientModel(itemStack, context);
    }

    @Override
    public void update(Level level, Entity player, MapItemSavedData data) {
        updateBanners(level, player, data);
    }

    @Override
    public void inventoryTick(ItemStack itemStack, ServerLevel level, Entity owner,
            @Nullable EquipmentSlot slot) {
        super.inventoryTick(itemStack, level, owner, slot);
        if (slot == null || slot.getType() != EquipmentSlot.Type.HAND
                || !(owner instanceof Player player)) {
            return;
        }
        MapItemSavedData data = MapItem.getSavedData(itemStack, level);
        if (data == null || data.locked) {
            return;
        }
        if (!this.kind.surveysIn(data.dimension)) {
            CaveMapStatic.update(level, player, data);
            return;
        }
        // Counted per holder of each map, which vanilla's own striped redraw no longer uses here.
        if (data.getHoldingPlayer(player).step++ % HELD_SURVEY_TICKS == 0) {
            CaveSurveys.request(level, player, itemStack);
        }
    }

    @Override
    public void onCraftedBy(ItemStack itemStack, Player player) {
        // Super applies any scale or lock post-processing first, so this surveys the new map.
        super.onCraftedBy(itemStack, player);
        if (player.level() instanceof ServerLevel serverLevel) {
            CaveSurveys.request(serverLevel, player, itemStack);
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
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        MapItemSavedData data = MapItem.getSavedData(itemStack, level);
        if (data != null && !this.kind.surveysIn(data.dimension)) {
            // A vanilla filled map does nothing when used.
            return InteractionResult.PASS;
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        if (data == null) {
            return InteractionResult.FAIL;
        }
        if (data.locked) {
            return InteractionResult.CONSUME;
        }
        CaveSurveys.request(serverLevel, player, itemStack);
        return InteractionResult.SUCCESS;
    }
}
