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

import ir.mehradn.cavesurvey.item.CaveMapItems;
import ir.mehradn.cavesurvey.item.CaveMapUpgrade;

/**
 * Adds the amethyst sight upgrade to the cartography table. Vanilla extends, locks and clones a
 * cave map itself, as it does any map in {@code #minecraft:extendable_maps}.
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

    // Takes over only a cave map with an amethyst shard, mirroring vanilla's setupResultSlot.
    @WrapOperation(method = "slotsChanged", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/CartographyTableMenu;setupResultSlot("
                    + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/item/ItemStack;)V"))
    private void cavesurvey$improveCaveMap(CartographyTableMenu menu, ItemStack map,
            ItemStack additional, ItemStack result, Operation<Void> original) {
        if (!map.is(CaveMapItems.FILLED_CAVE_MAP)
                || !additional.is(CaveMapUpgrade.IMPROVING.material())) {
            original.call(menu, map, additional, result);
            return;
        }
        this.access.execute((level, pos) -> {
            if (!CaveMapUpgrade.IMPROVING.canApply(map, level)) {
                this.resultContainer.removeItemNoUpdate(2);
                this.broadcastChanges();
                return;
            }
            ItemStack improved = CaveMapUpgrade.IMPROVING.apply(map);
            this.broadcastChanges();
            if (!ItemStack.matches(improved, result)) {
                this.resultContainer.setItem(2, improved);
                this.broadcastChanges();
            }
        });
    }

    // Shift-click routes an amethyst shard to the additional slot, as it does paper. On 26.3 these
    // calls compile to the erased TypedInstance.is(Object), not is(Item).
    @WrapOperation(method = "quickMoveStack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
    private boolean cavesurvey$routeAmethyst(ItemStack stack, Object item,
            Operation<Boolean> original) {
        if (item == Items.PAPER && stack.is(CaveMapUpgrade.IMPROVING.material())) {
            return true;
        }
        return original.call(stack, item);
    }
}
