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
 */
public record Params(
        double tellThreshold,
        double repeatWeight,
        double contradictionFactor,
        double dailyDecay,
        double forgetThreshold,
        double mutationChance,
        double plantedConfidence) {

    public Params {
        requireFraction(tellThreshold, "tellThreshold");
        requireFraction(repeatWeight, "repeatWeight");
        requireFraction(contradictionFactor, "contradictionFactor");
        requireFraction(dailyDecay, "dailyDecay");
        requireFraction(forgetThreshold, "forgetThreshold");
        requireFraction(mutationChance, "mutationChance");
        requireFraction(plantedConfidence, "plantedConfidence");
    }

    public static Params defaults() {
        return new Params(0.3, 0.25, 0.5, 0.9, 0.05, 0.05, 1.0);
    }

    private static void requireFraction(double value, String name) {
        if (!(value >= 0 && value <= 1)) {
            throw new IllegalArgumentException(name + " must be between 0 and 1, was " + value);
        }
    }
}
