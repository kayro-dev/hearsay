package hearsay;

import java.util.Collections;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * What one villager holds true, and how sure they are of it.
 *
 * @param claim      what is believed
 * @param confidence 0 to 1
 * @param sourceId   who last told them, or {@link #NO_SOURCE} if it was planted. This is
 *                   the most recent teller only, not the whole history: for that, see
 *                   {@link #chain()}
 * @param sinceTick  when it was last updated
 * @param rumorId    the version of the rumor this belief is held at, which is the most
 *                   severe version the villager has heard
 * @param chain      everyone this belief passed through on its way here, sorted. A
 *                   villager is never in their own chain. Hearing a claim back from
 *                   someone already in its chain is an echo of what they themselves put
 *                   into circulation, and convinces them of nothing
 */
public record Belief(Claim claim, double confidence, int sourceId, long sinceTick, int rumorId,
                     NavigableSet<Integer> chain) {

    /** Source id for a belief that came from nobody: a planted rumor. */
    public static final int NO_SOURCE = -1;

    /**
     * Source id for a belief a villager drew from the market price. No villager has this
     * id, so it sits harmlessly in a chain and simply never matches a teller.
     */
    public static final int MARKET = -2;

    /**
     * Source id for a belief a villager got from watching goods change hands. No villager
     * has this id either, so it sits in a chain and never matches a teller.
     *
     * <p>Distinct from {@link #MARKET} on purpose. The market is everyone's opinion at
     * once; this is nobody's opinion at all, and the difference is the whole point of it.
     * Seeing a thing cannot be a repeat of having been told about it.
     */
    public static final int SEEN = -3;

    public Belief {
        if (!(confidence >= 0 && confidence <= 1)) {
            throw new IllegalArgumentException("confidence must be between 0 and 1, was " + confidence);
        }
        chain = Collections.unmodifiableNavigableSet(new TreeSet<>(chain));
    }

    /** A belief that came from nobody and passed through nobody. */
    public static Belief planted(Claim claim, double confidence, long tick, int rumorId) {
        return new Belief(claim, confidence, NO_SOURCE, tick, rumorId, new TreeSet<>());
    }

    /** The same belief at a new strength, with its history intact. */
    public Belief withConfidence(double newConfidence) {
        return new Belief(claim, newConfidence, sourceId, sinceTick, rumorId, chain);
    }

    /** True if the holder worked this out from the price rather than being told. */
    public boolean cameFromTheMarket() {
        return chain.contains(MARKET);
    }

    /** True if this belief already passed through that villager on its way here. */
    public boolean cameThrough(int villagerId) {
        return chain.contains(villagerId);
    }
}
