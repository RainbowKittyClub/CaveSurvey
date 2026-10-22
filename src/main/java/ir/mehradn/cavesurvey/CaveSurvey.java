package ir.mehradn.cavesurvey;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import ir.mehradn.cavesurvey.event.ItemGroupModification;
import ir.mehradn.cavesurvey.event.LootTableModification;
import ir.mehradn.cavesurvey.item.CaveMapItems;
import ir.mehradn.cavesurvey.item.CaveMapSight;
import ir.mehradn.cavesurvey.item.CaveMapTab;
import ir.mehradn.cavesurvey.item.CaveSurveys;
import ir.mehradn.cavesurvey.item.crafting.CaveMapRecipes;

/** Mod entrypoint: registers the cave map items, their recipes, and loot and tab hooks. */
public class CaveSurvey implements ModInitializer {
    /** The mod id, which is also the namespace of every registry entry. */
    public static final String MOD_ID = "cave-survey";

    @Override
    public void onInitialize() {
        CaveMapSight.init();
        CaveMapItems.init();
        CaveMapRecipes.init();
        CaveMapTab.register();
        CaveSurveys.init();
        ItemGroupModification.register();
        LootTableModification.register();
        PolymerResourcePackUtils.addModAssets(MOD_ID);
        // Singleplayer turns Polymer's pack on only when a mod requires it; a server keeps the
        // pack optional with auto-host's mod_override off.
        PolymerResourcePackUtils.markAsRequired();
    }

    /** Returns an identifier in this mod's namespace. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
