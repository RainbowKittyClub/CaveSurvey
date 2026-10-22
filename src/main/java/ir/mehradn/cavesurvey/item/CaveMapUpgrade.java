package ir.mehradn.cavesurvey.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * The cave map upgrades vanilla has no recipe for. Cloning and extending go through vanilla's
 * {@code #minecraft:clonable_maps} and {@code #minecraft:extendable_maps} instead.
 */
public enum CaveMapUpgrade {
    /** Raises the sight level by one, with an amethyst shard. */
    IMPROVING(Items.AMETHYST_SHARD) {
        @Override
        public boolean canApply(ItemStack map, Level level) {
            return unlocked(map, level) && CaveMapSight.get(map) < CaveMapSight.MAX;
        }

        @Override
        public ItemStack apply(ItemStack map) {
            ItemStack result = map.copyWithCount(1);
            CaveMapSight.set(result, CaveMapSight.get(map) + 1);
            return result;
        }
    },
    /** Locks the map against further surveys, with glass panes; vanilla copies it on craft. */
    LOCKING(Items.GLASS_PANE) {
        @Override
        public boolean canApply(ItemStack map, Level level) {
            return unlocked(map, level);
        }

        @Override
        public ItemStack apply(ItemStack map) {
            ItemStack result = map.copyWithCount(1);
            result.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.LOCK);
            return result;
        }
    };

    private final Item material;

    CaveMapUpgrade(Item material) {
        this.material = material;
    }

    /** Returns the item that applies this upgrade. */
    public Item material() {
        return this.material;
    }

    /** Returns whether this upgrade applies to the given filled cave map. */
    public abstract boolean canApply(ItemStack map, Level level);

    /** Returns a single upgraded copy of the given filled cave map. */
    public abstract ItemStack apply(ItemStack map);

    // Whether the map has data and is not locked.
    private static boolean unlocked(ItemStack map, Level level) {
        MapItemSavedData data = MapItem.getSavedData(map, level);
        return data != null && !data.locked;
    }
}
