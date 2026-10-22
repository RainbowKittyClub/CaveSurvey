package ir.mehradn.cavesurvey.event;

import java.util.Map;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;

import ir.mehradn.cavesurvey.item.CaveMapItems;

/**
 * Adds empty cave maps to ancient city chests and empty Nether cave maps to bastion treasure
 * chests, each in a pool of its own: the map at weight 6 against nothing at weight 7.
 */
public final class LootTableModification {
    private LootTableModification() {
    }

    /** Registers the loot table hook. */
    public static void register() {
        Map<ResourceKey<LootTable>, Item> maps = Map.of(
                BuiltInLootTables.ANCIENT_CITY, CaveMapItems.CAVE_MAP,
                BuiltInLootTables.BASTION_TREASURE, CaveMapItems.NETHER_CAVE_MAP);
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            Item map = maps.get(key);
            if (map == null) {
                return;
            }
            tableBuilder.withPool(LootPool.lootPool()
                    .add(LootItem.lootTableItem(map).setWeight(6))
                    .add(EmptyLootItem.emptyItem().setWeight(7)));
        });
    }
}
