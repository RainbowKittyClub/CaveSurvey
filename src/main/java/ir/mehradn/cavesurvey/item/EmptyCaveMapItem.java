package ir.mehradn.cavesurvey.item;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** An empty cave map. Using it makes a filled cave map centred on the player and surveys once. */
public class EmptyCaveMapItem extends Item implements PolymerItem {
    /** Creates the item with the given properties. */
    public EmptyCaveMapItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
        return Items.MAP;
    }

    @Override
    public Identifier getPolymerItemModel(ItemStack itemStack, PacketContext context,
            HolderLookup.Provider lookup) {
        return CaveMapItems.clientModel(itemStack, context);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        itemStack.consume(1, player);
        player.awardStat(Stats.ITEM_USED.get(this));
        serverLevel.playSound(null, player, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                player.getSoundSource(), 1.0F, 1.0F);
        serverLevel.playSound(null, player, SoundEvents.SCULK_CLICKING, player.getSoundSource(),
                1.0F, 1.0F);

        ItemStack map = CaveMapItem.create(serverLevel, player.getBlockX(), player.getBlockZ());
        MapItemSavedData data = MapItem.getSavedData(map, serverLevel);
        if (data != null) {
            CaveMapItem.updateMap(serverLevel, player, data, CaveMapSight.get(map));
        }

        if (itemStack.isEmpty()) {
            return InteractionResult.SUCCESS.heldItemTransformedTo(map);
        }
        if (!player.getInventory().add(map.copy())) {
            player.drop(map, false, Prediction.SERVER_ONLY);
        }
        return InteractionResult.SUCCESS;
    }
}
