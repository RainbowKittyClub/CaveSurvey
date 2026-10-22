package ir.mehradn.cavesurvey.item;

import eu.pb4.polymer.core.api.item.PolymerCreativeModeTabUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import ir.mehradn.cavesurvey.CaveSurvey;

/**
 * This mod's own creative tab. Polymer lists it in {@code /polymer creative} and syncs it to
 * clients running Polymer.
 */
public final class CaveMapTab {
    private CaveMapTab() {
    }

    /** Registers the tab, once the items it lists are registered. */
    public static void register() {
        CreativeModeTab tab = PolymerCreativeModeTabUtils.builder()
                .title(Component.translatable("itemGroup." + CaveSurvey.MOD_ID + ".cave_survey"))
                .icon(() -> new ItemStack(CaveMapItems.FILLED_CAVE_MAP))
                .displayItems((params, output) -> {
                    output.accept(CaveMapItems.CAVE_MAP);
                    output.accept(CaveMapItems.NETHER_CAVE_MAP);
                })
                .build();
        PolymerCreativeModeTabUtils.registerPolymerCreativeModeTab(
                CaveSurvey.id("cave_survey"), tab);
    }
}
