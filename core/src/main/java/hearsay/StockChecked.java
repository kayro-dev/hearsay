package hearsay;

/**
 * A villager compared what they believed against what they could see, and moved.
 *
 * <p>The confidence is stored rather than recomputed, as everywhere else, so an old log
 * replays unchanged after the rule is retuned.
 *
 * <p>Who told them is not disturbed. A villager who heard it from Bo and has now seen the
 * chest still heard it from Bo; they are merely less sure. Provenance is a fact about how
 * the belief arrived and looking at a chest does not rewrite it.
 */
public record StockChecked(long tick, int villagerId, Claim claim, int rumorId,
                           double newConfidence) implements Event {}
