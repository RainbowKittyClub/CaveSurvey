package ir.mehradn.cavesurvey.item;

import java.util.function.Supplier;

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
import net.minecraft.world.level.Level;

/**
 * An empty cave map. Using it makes a filled map of its kind centred on the player, which surveys
 * at once where its kind works; elsewhere the map is made all the same, as a vanilla one is.
 */
public class EmptyCaveMapItem extends Item implements PolymerItem {
    private final CaveMapKind kind;
    private final Supplier<Item> filled;

    /** Creates an empty map of {@code kind} that becomes a {@code filled} item when used. */
    public EmptyCaveMapItem(CaveMapKind kind, Supplier<Item> filled, Item.Properties properties) {
        super(properties);
        this.kind = kind;
        this.filled = filled;
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
        if (this.kind.surveysIn(serverLevel.dimension())) {
            serverLevel.playSound(null, player, this.kind.makeSound(), player.getSoundSource(),
                    1.0F, 1.0F);
        }

        ItemStack map = CaveMapItem.create(serverLevel, player.getBlockX(), player.getBlockZ(),
                this.filled.get());
        CaveSurveys.request(serverLevel, player, map);

        if (itemStack.isEmpty()) {
            return InteractionResult.SUCCESS.heldItemTransformedTo(map);
        }
        if (!player.getInventory().add(map.copy())) {
            player.drop(map, false, Prediction.SERVER_ONLY);
        }
        return InteractionResult.SUCCESS;
    }
}
