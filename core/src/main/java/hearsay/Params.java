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
 * @param observationThreshold how far the price must stray from base before anyone reads
 *                            anything into it
 * @param marketNoise         the wobble on the settled price, as a fraction either way
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
        double marketNoise) {

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
        requireFraction(marketNoise, "marketNoise");
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
                100, 1.0, 0.15, 0.10, 0.03);
    }

    // Tuning one knob should not mean restating the other eleven, and a sweep that did
    // would silently shift its meaning every time a knob is added.

    public Params withTellThreshold(double value) {
        return new Params(value, repeatWeight, contradictionFactor, dailyDecay, forgetThreshold,
                mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, marketNoise);
    }

    public Params withDailyDecay(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, value, forgetThreshold,
                mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, marketNoise);
    }

    public Params withForgetThreshold(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay, value,
                mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, marketNoise);
    }

    public Params withMutationChance(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, value, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, marketNoise);
    }

    public Params withPriceSensitivity(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, mutationChance, plantedConfidence, basePrice, value,
                observationWeight, observationThreshold, marketNoise);
    }

    public Params withObservationWeight(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, mutationChance, plantedConfidence, basePrice, priceSensitivity,
                value, observationThreshold, marketNoise);
    }

    public Params withMarketNoise(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, value);
    }

    private static void requireFraction(double value, String name) {
        if (!(value >= 0 && value <= 1)) {
            throw new IllegalArgumentException(name + " must be between 0 and 1, was " + value);
        }
    }
}
