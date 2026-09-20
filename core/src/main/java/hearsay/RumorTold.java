package hearsay;

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
 */
public record RumorTold(long tick, int tellerId, int listenerId,
                        int toldRumorId, int keptRumorId, double newConfidence) implements Event {}
