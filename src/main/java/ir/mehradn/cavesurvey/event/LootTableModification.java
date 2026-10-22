package ir.mehradn.cavesurvey.event;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;

import ir.mehradn.cavesurvey.item.CaveMapItems;

/** Adds empty cave maps to ancient city chests. */
public final class LootTableModification {
    private LootTableModification() {
    }

    /** Registers the loot table hook. */
    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!key.equals(BuiltInLootTables.ANCIENT_CITY)) {
                return;
            }
            tableBuilder.withPool(LootPool.lootPool()
                    .add(LootItem.lootTableItem(CaveMapItems.CAVE_MAP).setWeight(6))
                    .add(EmptyLootItem.emptyItem().setWeight(7)));
        });
    }
}
