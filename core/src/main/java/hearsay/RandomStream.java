package hearsay;

import java.util.Random;

/**
 * One random generator per subsystem, each derived from the run's seed.
 *
 * <p>This is what makes counterfactuals meaningful. If movement and gossip shared a
 * generator, planting a rumor would consume extra draws and every later draw would shift,
 * so villagers would walk to different places in the counterfactual run. The difference
 * between the two runs would then be mostly noise rather than the effect of the rumor.
 * With separate streams, planting a rumor changes only what the rumor actually causes.
 */
public enum RandomStream {
    /** Where villagers go, and who they bump into once they are there. */
    MOVEMENT,
    /** Whether a villager tells what they believe. */
    GOSSIP,
    /** Whether a rumor grows in the telling. */
    MUTATION,
    /** The placeholder price walk, until prices follow beliefs in week 5. */
    PRICE;

    /**
     * A generator for this stream. The seeds are spread apart by the SplitMix64 mixing
     * function rather than by adding small numbers, so that neighbouring run seeds do not
     * produce streams that move in step with each other.
     */
    public Random from(long seed) {
        return new Random(mix(seed + GOLDEN_GAMMA * (ordinal() + 1)));
    }

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
