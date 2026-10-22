package ir.mehradn.cavesurvey.item.crafting;

import java.util.Map;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

import ir.mehradn.cavesurvey.item.CaveMapItems;
import ir.mehradn.cavesurvey.item.CaveMapUpgrade;

/** A filled cave map ringed by eight of an upgrade's material, as vanilla's map extending is. */
public final class CaveMapUpgradeRecipe extends CustomRecipe {
    /** The amethyst ring's serializer, which raises the sight level. */
    public static final RecipeSerializer<CaveMapUpgradeRecipe> IMPROVING_SERIALIZER =
            new CaveMapUpgradeRecipe(CaveMapUpgrade.IMPROVING).serializer;
    /** The glass-pane ring's serializer, which locks the map. */
    public static final RecipeSerializer<CaveMapUpgradeRecipe> LOCKING_SERIALIZER =
            new CaveMapUpgradeRecipe(CaveMapUpgrade.LOCKING).serializer;

    private final CaveMapUpgrade upgrade;
    private final ShapedRecipePattern pattern;
    // Always yields this recipe, since its JSON carries only the type.
    private final RecipeSerializer<CaveMapUpgradeRecipe> serializer;

    private CaveMapUpgradeRecipe(CaveMapUpgrade upgrade) {
        this.upgrade = upgrade;
        Ingredient maps = Ingredient.of(CaveMapItems.FILLED_CAVE_MAP,
                CaveMapItems.FILLED_NETHER_CAVE_MAP);
        this.pattern = ShapedRecipePattern.of(Map.of('#', Ingredient.of(upgrade.material()),
                'x', maps), "###", "#x#", "###");
        this.serializer = new RecipeSerializer<>(MapCodec.unit(this), StreamCodec.unit(this));
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        // The pattern's centre is a filled cave map, so findMap finds one once it matches.
        return this.pattern.matches(input) && this.upgrade.canApply(findMap(input), level);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack map = findMap(input);
        return map.isEmpty() ? ItemStack.EMPTY : this.upgrade.apply(map);
    }

    @Override
    public RecipeSerializer<CaveMapUpgradeRecipe> getSerializer() {
        return this.serializer;
    }

    // The filled cave map in the grid, or EMPTY.
    private static ItemStack findMap(CraftingInput input) {
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (CaveMapItems.isFilled(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
