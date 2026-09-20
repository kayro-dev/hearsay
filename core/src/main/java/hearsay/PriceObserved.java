package hearsay;

/**
 * A villager at the market read the price and drew a conclusion from it. This is the
 * second way a belief can start, alongside somebody planting a rumor.
 *
 * @param claim      what the price is evidence for: scarce if it is high, abundant if low
 * @param rumorId    the rumor the belief now points at, which is a fresh observation-born
 *                   rumor the first time anyone reaches this conclusion
 * @param newConfidence what the villager ends up believing, stored rather than recomputed
 */
public record PriceObserved(long tick, int villagerId, int price, Claim claim,
                            int rumorId, double newConfidence) implements Event {}
