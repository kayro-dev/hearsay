package hearsay;

import java.util.Collections;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * One villager told another something, and this is what the listener ended up with.
 *
 * <p>Both the confidence and the rumor kept are stored rather than recomputed during
 * replay. The log records what happened, not how it was worked out, so old logs still
 * replay correctly after the telling formula or the keep-the-worst rule is retuned.
 *
 * @param toldRumorId the version the teller passed on, possibly grown in the telling
 * @param keptRumorId the version the listener now holds: the more severe of what they
 *                    already had and what they were just told
 * @param witnesses   everyone standing there when it was said, the teller and listener
 *                    included. Saying something to three people is one event that all
 *                    three heard, not three private confidences, so each of them knows
 *                    the others were there. Two of them comparing notes later are
 *                    repeating one telling rather than confirming it, and this is what
 *                    lets the listener know that. For a conversation between two, this is
 *                    just the pair, which is why a perfectly mixed village is unaffected
 */
public record RumorTold(long tick, int tellerId, int listenerId,
                        int toldRumorId, int keptRumorId, double newConfidence,
                        NavigableSet<Integer> witnesses) implements Event {

    public RumorTold {
        witnesses = Collections.unmodifiableNavigableSet(new TreeSet<>(witnesses));
    }
}
