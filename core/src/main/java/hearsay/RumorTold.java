package hearsay;

/**
 * One villager told another something, and this is what the listener ended up believing.
 *
 * <p>The resulting confidence is stored rather than recomputed during replay. The log
 * records what happened, not how it was worked out, so old logs still replay correctly
 * after the telling formula is tuned.
 */
public record RumorTold(long tick, int tellerId, int listenerId, int rumorId, double newConfidence)
        implements Event {}
