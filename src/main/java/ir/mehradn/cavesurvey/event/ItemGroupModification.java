package ir.mehradn.cavesurvey.event;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;

import ir.mehradn.cavesurvey.item.CaveMapItems;

/** Lists the empty cave map after the empty map in the Tools and Utilities tab. */
public final class ItemGroupModification {
    private ItemGroupModification() {
    }

    /** Registers the creative tab hook. */
    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(output -> output.insertAfter(Items.MAP, CaveMapItems.CAVE_MAP));
    }
}
