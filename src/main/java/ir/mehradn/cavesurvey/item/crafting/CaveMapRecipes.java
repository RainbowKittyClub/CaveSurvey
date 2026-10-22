package ir.mehradn.cavesurvey.item.crafting;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

import ir.mehradn.cavesurvey.CaveSurvey;

/** Registers the cave map upgrade recipes' serializers, under upstream's ids. */
public final class CaveMapRecipes {
    private CaveMapRecipes() {
    }

    /** Registers the serializers. Recipe serializers are not in Fabric's registry sync. */
    public static void init() {
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
                CaveSurvey.id("crafting_special_cavemapimproving"),
                CaveMapUpgradeRecipe.IMPROVING_SERIALIZER);
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
                CaveSurvey.id("crafting_special_cavemaplocking"),
                CaveMapUpgradeRecipe.LOCKING_SERIALIZER);
    }
}
