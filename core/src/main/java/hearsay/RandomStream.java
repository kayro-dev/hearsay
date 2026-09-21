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
    /**
     * The wobble on the settled market price. Without it a village with no rumors would
     * hold a perfectly flat price forever, and a bubble arising on its own could not even
     * be measured.
     */
    MARKET,
    /**
     * Which neighbourhood a villager belongs to, and whether they stray out of it.
     *
     * <p>Appended rather than inserted: {@link #from} branches on the ordinal, so putting
     * this anywhere but last would hand every other subsystem a different stream and give
     * every existing seed a different village.
     */
    NEIGHBOURHOOD;

    /**
     * A generator for this stream. The seeds are spread apart by the SplitMix64 mixing
     * function rather than by adding small numbers, so that neighbouring run seeds do not
     * produce streams that move in step with each other.
     */
    public Random from(long seed) {
        return new Random(Seeds.branch(seed, ordinal()));
    }
}
