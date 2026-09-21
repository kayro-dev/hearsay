package hearsay;

/**
 * A villager watched diamonds change hands and made something of it.
 *
 * <p>The confidence is stored rather than recomputed, like every other event here, so an
 * old log still replays after the weighting is retuned.
 *
 * @param traded true if this villager did the trading, false if they only watched. The one
 *               who took the goods sees them most plainly, and weighs them more
 */
public record TradeSeen(long tick, int villagerId, Claim claim, int rumorId,
                        double newConfidence, boolean traded) implements Event {}
