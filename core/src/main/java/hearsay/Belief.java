package hearsay;

/**
 * What one villager holds true, and how sure they are of it.
 *
 * <p>The source is stored so that credibility and rumor family trees are possible later:
 * a belief always remembers who it came from.
 *
 * @param claim      what is believed
 * @param confidence 0 to 1
 * @param sourceId   the villager this came from, or {@link #NO_SOURCE} if held from birth
 * @param sinceTick  when it was last updated
 */
public record Belief(Claim claim, double confidence, int sourceId, long sinceTick) {

    /** Source id for a belief that came from nobody. */
    public static final int NO_SOURCE = -1;
}
