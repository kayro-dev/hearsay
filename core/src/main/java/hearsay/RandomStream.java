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

    /**
     * This stream for one good, so that goods never draw from each other's dice.
     *
     * <p>Diamond, first among the goods, gets exactly the stream {@link #from(long)} gives,
     * branched no further: a diamond-only village draws the same numbers it drew before any
     * other good existed. Every other good branches once more by its position, so a gold
     * telling can never shift a diamond draw — which it would, the moment they shared a
     * generator, and a counterfactual would then differ for reasons nothing to do with gold.
     */
    public Random from(long seed, Good good) {
        long mine = Seeds.branch(seed, ordinal());
        return new Random(good.ordinal() == 0 ? mine : Seeds.branch(mine, good.ordinal()));
    }
}
