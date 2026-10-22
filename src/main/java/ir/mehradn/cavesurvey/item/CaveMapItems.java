package ir.mehradn.cavesurvey.item;

import java.util.function.Function;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.MapDecorations;

import ir.mehradn.cavesurvey.CaveSurvey;

/** Registers the empty and filled cave map items. */
public final class CaveMapItems {
    /** The empty cave map, which becomes a filled one centred on the player when used. */
    public static final Item CAVE_MAP = register("cave_map", EmptyCaveMapItem::new,
            new Item.Properties().rarity(Rarity.UNCOMMON));
    /** The filled cave map. Its properties mirror vanilla's filled map, plus a sight level. */
    public static final Item FILLED_CAVE_MAP = register("filled_cave_map", CaveMapItem::new,
            new Item.Properties()
                    .component(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY)
                    .component(CaveMapSight.SIGHT, 0));

    private CaveMapItems() {
    }

    /** Forces registration; the fields above do the work. */
    public static void init() {
    }

    /**
     * Returns the item model a client is sent for a cave map: its own when the client has the
     * server's resource pack, else null, which makes Polymer send the vanilla map's model.
     */
    public static Identifier clientModel(ItemStack stack, PacketContext context) {
        return PolymerResourcePackUtils.hasMainPack(context)
                ? stack.get(DataComponents.ITEM_MODEL) : null;
    }

    // Items.registerItem is private, and 26.x items need their id set before construction.
    private static Item register(String name, Function<Item.Properties, Item> factory,
            Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CaveSurvey.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }
}
