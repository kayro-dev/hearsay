package hearsay;

/**
 * A villager's fixed personality, rolled once at creation. Every value is 0 to 1.
 *
 * @param gossip    how eager they are to share news (week 4)
 * @param credulity how easily a rumor convinces them (week 4)
 * @param greed     how much profit drives their pricing (week 5)
 */
public record Traits(double gossip, double credulity, double greed) {}
