package ir.mehradn.cavesurvey.item;

import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;

/**
 * Where a kind of cave map surveys. Anywhere else its map behaves as a vanilla map does in the
 * Nether: it still fills with the ceiling static, and never surveys.
 */
public enum CaveMapKind {
    /** The cave map: everywhere but the Nether. Made with an echo shard. */
    CAVE(SoundEvents.SCULK_CLICKING) {
        @Override
        public boolean surveysIn(ResourceKey<Level> dimension) {
            return !Level.NETHER.equals(dimension);
        }
    },
    /** The Nether cave map: the Nether only. Made with crying obsidian. */
    NETHER(SoundEvents.RESPAWN_ANCHOR_CHARGE) {
        @Override
        public boolean surveysIn(ResourceKey<Level> dimension) {
            return Level.NETHER.equals(dimension);
        }
    };

    private final SoundEvent makeSound;

    CaveMapKind(SoundEvent makeSound) {
        this.makeSound = makeSound;
    }

    /** Returns the sound an empty map of this kind makes when it becomes a filled one. */
    public SoundEvent makeSound() {
        return this.makeSound;
    }

    /** Returns whether a map of this kind, made in {@code dimension}, surveys there. */
    public abstract boolean surveysIn(ResourceKey<Level> dimension);
}
