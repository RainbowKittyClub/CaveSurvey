package ir.mehradn.cavesurvey.item;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
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

    // Items.registerItem is private, and 26.x items need their id set before construction.
    private static Item register(String name, Function<Item.Properties, Item> factory,
            Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CaveSurvey.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }
}
