package hearsay;

/**
 * A specific version of a claim in circulation. Rumors form a family tree: a rumor that
 * grew in the telling keeps a link to the one it grew from, so any belief can be traced
 * back to the planted rumor it descends from.
 *
 * <p>Ids come from a counter in {@link WorldState}, never from randomness, so they stay
 * the same whenever the same events are replayed.
 *
 * @param parentId the rumor this grew from, or {@link #NO_PARENT} if it was planted
 */
public record Rumor(int id, Claim claim, int severity, int parentId, long createdTick) {

    public static final int NO_PARENT = -1;
    public static final int MIN_SEVERITY = 1;
    public static final int MAX_SEVERITY = 3;

    public Rumor {
        if (severity < MIN_SEVERITY || severity > MAX_SEVERITY) {
            throw new IllegalArgumentException("severity must be 1 to 3, was " + severity);
        }
    }

    public boolean isPlanted() { return parentId == NO_PARENT; }
}
