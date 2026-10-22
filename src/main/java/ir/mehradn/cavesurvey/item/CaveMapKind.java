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
    /**
     * The cave map: everywhere but the Nether, and only under cover; a floor open to the sky shows
     * the ceiling static. Made with an echo shard.
     */
    CAVE(SoundEvents.SCULK_CLICKING, false) {
        @Override
        public boolean surveysIn(ResourceKey<Level> dimension) {
            return !Level.NETHER.equals(dimension);
        }
    },
    /** The Nether cave map: the Nether only, roof included. Made with crying obsidian. */
    NETHER(SoundEvents.RESPAWN_ANCHOR_CHARGE, true) {
        @Override
        public boolean surveysIn(ResourceKey<Level> dimension) {
            return Level.NETHER.equals(dimension);
        }
    };

    private final SoundEvent makeSound;
    private final boolean drawsOpenSky;

    CaveMapKind(SoundEvent makeSound, boolean drawsOpenSky) {
        this.makeSound = makeSound;
        this.drawsOpenSky = drawsOpenSky;
    }

    /** Returns the sound an empty map of this kind makes when it becomes a filled one. */
    public SoundEvent makeSound() {
        return this.makeSound;
    }

    /** Returns whether this kind draws floors open to the sky, rather than the ceiling static. */
    public boolean drawsOpenSky() {
        return this.drawsOpenSky;
    }

    /** Returns whether a map of this kind, made in {@code dimension}, surveys there. */
    public abstract boolean surveysIn(ResourceKey<Level> dimension);
}
