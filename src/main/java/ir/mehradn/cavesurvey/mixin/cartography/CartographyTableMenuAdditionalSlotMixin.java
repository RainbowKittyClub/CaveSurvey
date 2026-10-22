package ir.mehradn.cavesurvey.mixin.cartography;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.item.ItemStack;

import ir.mehradn.cavesurvey.item.CaveMapItems;
import ir.mehradn.cavesurvey.item.CaveMapUpgrade;

/**
 * Lets the cartography table's additional slot take an amethyst shard, the sight upgrade, and an
 * empty cave map, which clones a cave map.
 */
@Mixin(targets = "net.minecraft.world.inventory.CartographyTableMenu$4")
public abstract class CartographyTableMenuAdditionalSlotMixin {
    // $4 is the additional slot in 26.3: $3 is the map slot, $5 the result slot.
    @ModifyReturnValue(method = "mayPlace", at = @At("RETURN"))
    private boolean cavesurvey$acceptCaveMapExtras(boolean original, ItemStack itemStack) {
        return original || itemStack.is(CaveMapUpgrade.IMPROVING.material())
                || itemStack.is(CaveMapItems.CAVE_MAP);
    }
}
