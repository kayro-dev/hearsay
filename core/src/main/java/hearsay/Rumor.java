package hearsay;

/**
 * A specific version of a claim in circulation. Rumors form a family tree: a rumor that
 * grew in the telling keeps a link to the one it grew from, so any belief can be traced
 * back to where its family started.
 *
 * <p>A family starts either because somebody planted it or because a villager drew a
 * conclusion from the market price. Once it exists, a rumor born of observation is told
 * and exaggerated exactly like any other.
 *
 * <p>Ids come from a counter in {@link WorldState}, never from randomness, so they stay
 * the same whenever the same events are replayed.
 *
 * @param parentId the rumor this grew from, or {@link #NO_PARENT} if it starts a family
 */
public record Rumor(int id, Claim claim, int severity, int parentId, long createdTick,
                    RumorOrigin origin) {

    public static final int NO_PARENT = -1;
    public static final int MIN_SEVERITY = 1;
    public static final int MAX_SEVERITY = 3;

    public Rumor {
        if (severity < MIN_SEVERITY || severity > MAX_SEVERITY) {
            throw new IllegalArgumentException("severity must be 1 to 3, was " + severity);
        }
    }

    /** True if this rumor starts a family rather than growing out of another. */
    public boolean isRoot() { return parentId == NO_PARENT; }

    /** True if this rumor was put into the village from outside. */
    public boolean isPlanted() { return isRoot() && origin == RumorOrigin.PLANTED; }

    /** True if this rumor started with somebody reading the market price. */
    public boolean isObserved() { return isRoot() && origin == RumorOrigin.OBSERVED; }
}
