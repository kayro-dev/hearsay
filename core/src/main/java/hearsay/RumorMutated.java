package hearsay;

/**
 * A rumor grew in the telling. Recorded just before the telling it happens during; the
 * claim is inherited from the parent, so only the new severity needs storing.
 */
public record RumorMutated(long tick, int rumorId, int parentId, int severity) implements Event {}
