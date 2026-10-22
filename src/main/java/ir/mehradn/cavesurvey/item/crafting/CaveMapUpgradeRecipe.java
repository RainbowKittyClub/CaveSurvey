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
    /** The amethyst ring that raises the sight level. */
    public static final CaveMapUpgradeRecipe IMPROVING =
            new CaveMapUpgradeRecipe(CaveMapUpgrade.IMPROVING);
    /** The glass-pane ring that locks the map. */
    public static final CaveMapUpgradeRecipe LOCKING =
            new CaveMapUpgradeRecipe(CaveMapUpgrade.LOCKING);
    /** The serializer of {@link #IMPROVING}; the recipe has no fields to read. */
    public static final RecipeSerializer<CaveMapUpgradeRecipe> IMPROVING_SERIALIZER =
            serializer(IMPROVING);
    /** The serializer of {@link #LOCKING}; the recipe has no fields to read. */
    public static final RecipeSerializer<CaveMapUpgradeRecipe> LOCKING_SERIALIZER =
            serializer(LOCKING);

    private final CaveMapUpgrade upgrade;
    private final ShapedRecipePattern pattern;

    private CaveMapUpgradeRecipe(CaveMapUpgrade upgrade) {
        this.upgrade = upgrade;
        this.pattern = ShapedRecipePattern.of(Map.of('#', Ingredient.of(upgrade.material()),
                'x', Ingredient.of(CaveMapItems.FILLED_CAVE_MAP)), "###", "#x#", "###");
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (!this.pattern.matches(input)) {
            return false;
        }
        ItemStack map = findMap(input);
        return !map.isEmpty() && this.upgrade.canApply(map, level);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack map = findMap(input);
        return map.isEmpty() ? ItemStack.EMPTY : this.upgrade.apply(map);
    }

    @Override
    public RecipeSerializer<CaveMapUpgradeRecipe> getSerializer() {
        return this.upgrade == CaveMapUpgrade.IMPROVING ? IMPROVING_SERIALIZER : LOCKING_SERIALIZER;
    }

    // A serializer that always yields the given recipe, since its JSON carries only the type.
    private static RecipeSerializer<CaveMapUpgradeRecipe> serializer(CaveMapUpgradeRecipe recipe) {
        return new RecipeSerializer<>(MapCodec.unit(recipe), StreamCodec.unit(recipe));
    }

    // The filled cave map in the grid, or EMPTY.
    private static ItemStack findMap(CraftingInput input) {
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.is(CaveMapItems.FILLED_CAVE_MAP)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
