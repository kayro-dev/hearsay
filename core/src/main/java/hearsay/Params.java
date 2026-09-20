package hearsay;

/**
 * Every tuning knob in one place. Together with the seed and the input events, a Params
 * value is the full recipe for a run: save those three and the run can be reproduced.
 *
 * @param tellThreshold       a belief weaker than this is not worth mentioning
 * @param repeatWeight        how much evidence a source already in the listener's chain
 *                            carries, next to 1.0 for an independent one
 * @param contradictionFactor multiplier when the listener believes the opposite claim
 * @param dailyDecay          confidence is multiplied by this at the end of each day
 * @param forgetThreshold     a belief weaker than this is dropped entirely
 * @param mutationChance      chance that a rumor grows in the telling
 * @param plantedConfidence   how sure the villager a rumor is planted in starts out
 * @param basePrice           what a diamond is worth when nobody believes anything
 * @param priceSensitivity    how far belief moves a villager's asking price
 * @param observationWeight   how much evidence the market price itself carries
 * @param observationThreshold how far the price must move before anyone reads anything
 *                            into it at all
 * @param fullMoveSize        the move that counts as complete evidence. A move this big
 *                            or bigger carries the whole observationWeight; a smaller one
 *                            that still passes the threshold carries its share. Without
 *                            it the weight would be scaled by the raw move, which is a
 *                            fraction of a fraction and leaves observationWeight with
 *                            nowhere useful to sit
 * @param marketNoise         the size of each step in the price wobble, as a fraction
 * @param noiseDecay          how much of yesterday's wobble carries into today, which is
 *                            what lets a streak build far enough to be noticed
 * @param marketQuorum        fewer sellers than this and the market does not open
 */
public record Params(
        double tellThreshold,
        double repeatWeight,
        double contradictionFactor,
        double dailyDecay,
        double forgetThreshold,
        double mutationChance,
        double plantedConfidence,
        int basePrice,
        double priceSensitivity,
        double observationWeight,
        double observationThreshold,
        double fullMoveSize,
        double marketNoise,
        double noiseDecay,
        int marketQuorum) {

    public Params {
        requireFraction(tellThreshold, "tellThreshold");
        requireFraction(repeatWeight, "repeatWeight");
        requireFraction(contradictionFactor, "contradictionFactor");
        requireFraction(dailyDecay, "dailyDecay");
        requireFraction(forgetThreshold, "forgetThreshold");
        requireFraction(mutationChance, "mutationChance");
        requireFraction(plantedConfidence, "plantedConfidence");
        requireFraction(observationWeight, "observationWeight");
        requireFraction(observationThreshold, "observationThreshold");
        if (!(fullMoveSize > 0)) {
            throw new IllegalArgumentException("fullMoveSize must be positive, was " + fullMoveSize);
        }
        requireFraction(marketNoise, "marketNoise");
        requireFraction(noiseDecay, "noiseDecay");
        if (marketQuorum < 2) {
            throw new IllegalArgumentException("marketQuorum must be at least 2, was " + marketQuorum);
        }
        if (basePrice < 1) {
            throw new IllegalArgumentException("basePrice must be at least 1, was " + basePrice);
        }
        if (!(priceSensitivity >= 0)) {
            throw new IllegalArgumentException(
                    "priceSensitivity must not be negative, was " + priceSensitivity);
        }
    }

    /**
     * The rumor knobs were chosen from a seed sweep rather than by hand: see
     * EXPERIMENTS.md. A decay of 0.92 with a telling threshold of 0.4 gives a mean peak of
     * 4.7 believers out of 20, a grip lasting about eight days, and no seed in fifty
     * running away to convince the village. Deliberately modest, because the market
     * feedback loop is meant to do the work of turning a rumor into a bubble. The market
     * knobs are the design's starting values and have not been swept yet.
     */
    public static Params defaults() {
        return new Params(0.4, 0.25, 0.5, 0.92, 0.05, 0.05, 1.0,
                100, 1.0, 0.15, 0.10, 0.20, 0.03, 0.8, 5);
    }

    // Tuning one knob should not mean restating the other eleven, and a sweep that did
    // would silently shift its meaning every time a knob is added.

    public Params withTellThreshold(double value) {
        return new Params(value, repeatWeight, contradictionFactor, dailyDecay, forgetThreshold,
                mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, fullMoveSize, marketNoise, noiseDecay,
                marketQuorum);
    }

    public Params withDailyDecay(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, value, forgetThreshold,
                mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, fullMoveSize, marketNoise, noiseDecay,
                marketQuorum);
    }

    public Params withForgetThreshold(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay, value,
                mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, fullMoveSize, marketNoise, noiseDecay,
                marketQuorum);
    }

    public Params withMutationChance(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, value, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, fullMoveSize, marketNoise, noiseDecay,
                marketQuorum);
    }

    public Params withPriceSensitivity(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, mutationChance, plantedConfidence, basePrice, value,
                observationWeight, observationThreshold, fullMoveSize, marketNoise, noiseDecay,
                marketQuorum);
    }

    public Params withObservationWeight(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, mutationChance, plantedConfidence, basePrice, priceSensitivity,
                value, observationThreshold, fullMoveSize, marketNoise, noiseDecay, marketQuorum);
    }

    public Params withNoiseDecay(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, fullMoveSize, marketNoise, value,
                marketQuorum);
    }

    public Params withFullMoveSize(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, value, marketNoise, noiseDecay,
                marketQuorum);
    }

    public Params withMarketNoise(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, fullMoveSize, value, noiseDecay,
                marketQuorum);
    }

    private static void requireFraction(double value, String name) {
        if (!(value >= 0 && value <= 1)) {
            throw new IllegalArgumentException(name + " must be between 0 and 1, was " + value);
        }
    }
}
