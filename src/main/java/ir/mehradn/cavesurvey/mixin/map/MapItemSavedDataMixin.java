package ir.mehradn.cavesurvey.mixin.map;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import ir.mehradn.cavesurvey.item.CaveMapItem;

/**
 * Points the markers of a cave map that surveys its dimension the way they face, where vanilla
 * spins every marker on a Nether map.
 */
@Mixin(MapItemSavedData.class)
public abstract class MapItemSavedDataMixin {
    @Shadow
    @Final
    public ResourceKey<Level> dimension;

    // Whether the stack that last ticked this map is a cave map that surveys its dimension; one
    // map id belongs to one kind, so it holds for banners toggled between ticks too.
    @Unique
    private boolean cavesurvey$pointsTrue;

    @Inject(method = "tickCarriedBy", at = @At("HEAD"))
    private void cavesurvey$noteCaveMap(Player player, ItemStack itemStack, ItemFrame frame,
            CallbackInfo ci) {
        this.cavesurvey$pointsTrue = itemStack.getItem() instanceof CaveMapItem map
                && map.kind().surveysIn(this.dimension);
    }

    // calculateRotation spins markers only when this reads as the Nether.
    @ModifyExpressionValue(method = "calculateRotation", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;dimension:"
                    + "Lnet/minecraft/resources/ResourceKey;"))
    private ResourceKey<Level> cavesurvey$pointTrue(ResourceKey<Level> original) {
        return this.cavesurvey$pointsTrue ? Level.OVERWORLD : original;
    }
}
