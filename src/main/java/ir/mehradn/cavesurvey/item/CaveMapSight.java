package ir.mehradn.cavesurvey.item;

import eu.pb4.polymer.core.api.other.PolymerComponent;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import ir.mehradn.cavesurvey.CaveSurvey;

/** The sight level of a cave map, which sets how far one use of the map surveys. */
public final class CaveMapSight {
    /** The highest sight level. */
    public static final int MAX = 2;
    /** The item component holding a cave map's sight level, 0 to {@link #MAX}. */
    public static final DataComponentType<Integer> SIGHT = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, CaveSurvey.id("sight"),
            DataComponentType.<Integer>builder().persistent(ExtraCodecs.intRange(0, MAX)).build());

    static {
        // Server-side only: hidden from Fabric's registry sync and stripped from client stacks.
        PolymerComponent.registerDataComponent(SIGHT);
    }

    private CaveMapSight() {
    }

    /** Forces registration; the field above and the static block do the work. */
    public static void init() {
    }

    /** Returns the stack's sight level, 0 when it has none. */
    public static int get(ItemStack stack) {
        return stack.getOrDefault(SIGHT, 0);
    }

    /** Sets the stack's sight level, clamped to the valid range. */
    public static void set(ItemStack stack, int sight) {
        stack.set(SIGHT, Mth.clamp(sight, 0, MAX));
    }

    /** Returns how many blocks around its holder a map at this sight level surveys. */
    public static int blockRadius(int sight) {
        return 1 << (sight + 4);
    }
}
