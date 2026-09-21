package hearsay;

/**
 * The market settled on a price this tick: the average of the asking prices of the
 * villagers standing there, nudged by a little noise.
 *
 * <p>The resulting price is stored rather than recomputed during replay. Retuning the
 * price formula must not change what an old log replays to.
 */
public record MarketPriceSet(long tick, int price, int askingVillagers) implements Event {}
