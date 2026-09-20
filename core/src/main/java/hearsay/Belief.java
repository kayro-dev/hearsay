package hearsay;

/**
 * What one villager holds true, and how sure they are of it.
 *
 * <p>Both the teller and the exact rumor are stored, so a belief can be traced to the
 * version of the rumor it came from and, through that rumor's parents, back to the
 * planted rumor it descends from. That is what makes credibility and rumor family trees
 * possible later.
 *
 * @param claim      what is believed
 * @param confidence 0 to 1
 * @param sourceId   the villager this came from, or {@link #NO_SOURCE} if it was planted
 * @param sinceTick  when it was last updated
 * @param rumorId    the exact rumor this belief came from
 */
public record Belief(Claim claim, double confidence, int sourceId, long sinceTick, int rumorId) {

    /** Source id for a belief that came from nobody: a planted rumor. */
    public static final int NO_SOURCE = -1;

    public Belief {
        if (!(confidence >= 0 && confidence <= 1)) {
            throw new IllegalArgumentException("confidence must be between 0 and 1, was " + confidence);
        }
    }

    /** The same belief at a new strength. */
    public Belief withConfidence(double newConfidence) {
        return new Belief(claim, newConfidence, sourceId, sinceTick, rumorId);
    }
}
