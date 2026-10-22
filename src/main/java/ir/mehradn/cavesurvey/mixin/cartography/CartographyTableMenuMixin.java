package ir.mehradn.cavesurvey.mixin.cartography;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;

import ir.mehradn.cavesurvey.item.CaveMapItems;
import ir.mehradn.cavesurvey.item.CaveMapUpgrade;

/**
 * Adds the amethyst sight upgrade and cloning with an empty cave map to the cartography table.
 * Vanilla extends, locks and clones (with an empty map) a cave map itself, as it does any map in
 * {@code #minecraft:extendable_maps}.
 */
@Mixin(CartographyTableMenu.class)
public abstract class CartographyTableMenuMixin extends AbstractContainerMenu {
    @Shadow
    @Final
    private ContainerLevelAccess access;
    @Shadow
    @Final
    private ResultContainer resultContainer;

    /** Mixin stub, never called. */
    protected CartographyTableMenuMixin(MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }

    // Takes over a cave map with an amethyst shard or an empty cave map, mirroring vanilla's
    // setupResultSlot; everything else goes to vanilla.
    @WrapOperation(method = "slotsChanged", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/CartographyTableMenu;setupResultSlot("
                    + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/item/ItemStack;)V"))
    private void cavesurvey$setupCaveMapResult(CartographyTableMenu menu, ItemStack map,
            ItemStack additional, ItemStack result, Operation<Void> original) {
        boolean improving = additional.is(CaveMapUpgrade.IMPROVING.material());
        boolean cloning = additional.is(CaveMapItems.CAVE_MAP);
        if (!map.is(CaveMapItems.FILLED_CAVE_MAP) || !(improving || cloning)) {
            original.call(menu, map, additional, result);
            return;
        }
        this.access.execute((level, pos) -> {
            boolean valid = cloning ? MapItem.getSavedData(map, level) != null
                    : CaveMapUpgrade.IMPROVING.canApply(map, level);
            if (!valid) {
                this.resultContainer.removeItemNoUpdate(2);
                this.broadcastChanges();
                return;
            }
            ItemStack made = cloning ? map.copyWithCount(2) : CaveMapUpgrade.IMPROVING.apply(map);
            this.broadcastChanges();
            if (!ItemStack.matches(made, result)) {
                this.resultContainer.setItem(2, made);
                this.broadcastChanges();
            }
        });
    }

    // Shift-click routes an amethyst shard or an empty cave map to the additional slot, as it does
    // paper. On 26.3 these calls compile to the erased TypedInstance.is(Object), not is(Item).
    @WrapOperation(method = "quickMoveStack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
    private boolean cavesurvey$routeCaveMapExtras(ItemStack stack, Object item,
            Operation<Boolean> original) {
        if (item == Items.PAPER && (stack.is(CaveMapUpgrade.IMPROVING.material())
                || stack.is(CaveMapItems.CAVE_MAP))) {
            return true;
        }
        return original.call(stack, item);
    }
}
