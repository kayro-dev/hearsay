package hearsay;

/**
 * Every tuning knob in one place. Together with the seed and the input events, a Params
 * value is the full recipe for a run: save those three and the run can be reproduced.
 *
 * @param tellThreshold       a belief weaker than this is not worth mentioning. Low on
 *                            purpose: people repeat things they only half believe, and at
 *                            0.4 a freshly heard rumor fell under it within two days and
 *                            was never passed on again, which left the market doing nearly
 *                            all the convincing (E29)
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
 * @param marketQuorumFraction how much of the village must be standing in the market
 *                            before it opens, as a share. A share rather than a count,
 *                            because a count picked for a village of twenty demands
 *                            everybody in a village of five, and a market that can never
 *                            open shows no price at all
 * @param marketWindowTicks   how long a villager keeps counting as a trader after leaving
 *                            the market. Where a villager stands is learned in glimpses,
 *                            so a market read from one instant misses most of who is in
 *                            it; a little grace turns a snapshot into a sample
 * @param meetingSource       who decides which villagers meet: Hearsay, or something
 *                            outside it. Not a knob to tune, but part of what reproduces
 *                            a run, which is why it lives here
 * @param tradeWeight         how much taking diamonds off somebody convinces the villager
 *                            who took them that diamonds are not scarce after all
 * @param witnessWeight       the same, for a villager who only watched it happen. Seeing a
 *                            thing yourself and seeing somebody else see it are different
 *                            amounts of evidence
 * @param mixing              the chance a villager joins the village-wide pool for a
 *                            meeting rather than only meeting their own neighbourhood.
 *                            At 1.0 there are no neighbourhoods and the village is
 *                            perfectly mixed, which is what it was before E23
 * @param villagers           how many villagers the village has. A real village is
 *                            whatever size it is, and a figure counted out of twenty
 *                            means nothing in a village of nine
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
        double marketQuorumFraction,
        int marketWindowTicks,
        MeetingSource meetingSource,
        int villagers,
        double mixing,
        double tradeWeight,
        double witnessWeight) {

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
        if (villagers < 2 || villagers > Simulation.MOST_VILLAGERS) {
            throw new IllegalArgumentException("villagers must be 2 to "
                    + Simulation.MOST_VILLAGERS + ", was " + villagers);
        }
        if (meetingSource == null) {
            throw new IllegalArgumentException("meetingSource must be given");
        }
        requireFraction(marketQuorumFraction, "marketQuorumFraction");
        requireFraction(mixing, "mixing");
        requireFraction(tradeWeight, "tradeWeight");
        requireFraction(witnessWeight, "witnessWeight");
        if (marketWindowTicks < 0) {
            throw new IllegalArgumentException(
                    "marketWindowTicks cannot be negative, was " + marketWindowTicks);
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
     * Every value here was chosen from a seed sweep rather than by hand: see
     * EXPERIMENTS.md.
     *
     * <p>The rumor knobs are deliberately modest, since the market loop is meant to do the
     * work of turning a rumor into a bubble: a decay of 0.92 with a telling threshold of
     * 0.4 gives a mean peak of 4.7 believers out of 20 and a grip lasting about eight days.
     *
     * <p>The market knobs come from E5. On a hundred seeds the sweep never saw, a planted
     * rumor convinces half the village in 65% of them and the price bursts in 94%, while a
     * village nobody lied to produces a believer in 5% and bursts in about one seed in three
     * hundred, measured across nine hundred of them rather than assumed. CalibrationTest
     * holds those figures in place.
     *
     * <p>{@code observationWeight} is the touchy one: a change of 0.05 either way moves the
     * half-believing rate by twenty points or more, so it wants re-validating rather than
     * nudging. It was raised once the village was clustered, because the market price is the
     * only evidence that crosses neighbourhood lines and a clustered village needs it to
     * carry more. 0.34 and above starts bursting villages nobody lied to, and E26 settled
     * on 0.28 rather than 0.32 because 0.32 pins a village of twenty-six against the price
     * cap: the weight was fitted on villages of about twenty and does not transfer upward.
     */
    /**
     * Villagers to a neighbourhood. Fitted in E23 against recorded position traces, not
     * chosen: at seven the model could not be made to cluster as tightly as a real
     * village does however low {@code mixing} went.
     */
    private static final float PER_NEIGHBOURHOOD = 3f;

    /**
     * Fitted in E23 so the model meets as few distinct people in two days as a played
     * village does. At 1.0 the village is perfectly mixed, which is what it was before.
     */
    public static final double MIXING = 0.05;

    /**
     * How much a villager is convinced of plenty by taking diamonds off somebody, and how
     * much by watching somebody else do it. Provisional until E33 sweeps them; the one who
     * took the goods must weigh them at least as heavily as the one who only watched.
     */
    public static final double TRADE_WEIGHT = 0.28;
    public static final double WITNESS_WEIGHT = 0.14;

    public static Params defaults() {
        return new Params(0.25, 0.25, 0.5, 0.92, 0.05, 0.05, 1.0,
                100, 0.75, 0.28, 0.10, 0.20, 0.030, 0.86, 0.25, 0, MeetingSource.SIMULATED,
                Simulation.VILLAGER_COUNT, MIXING, TRADE_WEIGHT, WITNESS_WEIGHT);
    }

    // Tuning one knob should not mean restating the other eleven, and a sweep that did
    // would silently shift its meaning every time a knob is added.

    public Params withTellThreshold(double value) {
        return new Params(value, repeatWeight, contradictionFactor, dailyDecay, forgetThreshold,
                mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, fullMoveSize, marketNoise, noiseDecay,
                marketQuorumFraction, marketWindowTicks, meetingSource, villagers, mixing, tradeWeight, witnessWeight);
    }

    public Params withDailyDecay(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, value, forgetThreshold,
                mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, fullMoveSize, marketNoise, noiseDecay,
                marketQuorumFraction, marketWindowTicks, meetingSource, villagers, mixing, tradeWeight, witnessWeight);
    }

    public Params withForgetThreshold(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay, value,
                mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, fullMoveSize, marketNoise, noiseDecay,
                marketQuorumFraction, marketWindowTicks, meetingSource, villagers, mixing, tradeWeight, witnessWeight);
    }

    public Params withMutationChance(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, value, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, fullMoveSize, marketNoise, noiseDecay,
                marketQuorumFraction, marketWindowTicks, meetingSource, villagers, mixing, tradeWeight, witnessWeight);
    }

    public Params withPriceSensitivity(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, mutationChance, plantedConfidence, basePrice, value,
                observationWeight, observationThreshold, fullMoveSize, marketNoise, noiseDecay,
                marketQuorumFraction, marketWindowTicks, meetingSource, villagers, mixing, tradeWeight, witnessWeight);
    }

    public Params withObservationWeight(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, mutationChance, plantedConfidence, basePrice, priceSensitivity,
                value, observationThreshold, fullMoveSize, marketNoise, noiseDecay, marketQuorumFraction, marketWindowTicks, meetingSource, villagers, mixing, tradeWeight, witnessWeight);
    }

    public Params withNoiseDecay(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, fullMoveSize, marketNoise, value,
                marketQuorumFraction, marketWindowTicks, meetingSource, villagers, mixing, tradeWeight, witnessWeight);
    }

    public Params withObservationThreshold(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, value, fullMoveSize, marketNoise, noiseDecay,
                marketQuorumFraction, marketWindowTicks, meetingSource, villagers, mixing, tradeWeight, witnessWeight);
    }

    public Params withFullMoveSize(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, value, marketNoise, noiseDecay,
                marketQuorumFraction, marketWindowTicks, meetingSource, villagers, mixing, tradeWeight, witnessWeight);
    }

    public Params withMarketNoise(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, fullMoveSize, value, noiseDecay,
                marketQuorumFraction, marketWindowTicks, meetingSource, villagers, mixing, tradeWeight, witnessWeight);
    }

    /** The same settings with meetings coming from outside instead of being simulated. */
    public Params withMeetingSource(MeetingSource value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, fullMoveSize, marketNoise, noiseDecay,
                marketQuorumFraction, marketWindowTicks, value, villagers, mixing, tradeWeight, witnessWeight);
    }

    /**
     * How many sellers the market needs, for this village. Never fewer than two, because
     * one villager alone is not a market, and never more than the village holds.
     */
    public Params withTradeWeight(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, fullMoveSize, marketNoise, noiseDecay,
                marketQuorumFraction, marketWindowTicks, meetingSource, villagers, mixing,
                value, witnessWeight);
    }

    public Params withWitnessWeight(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, fullMoveSize, marketNoise, noiseDecay,
                marketQuorumFraction, marketWindowTicks, meetingSource, villagers, mixing,
                tradeWeight, value);
    }

    public Params withMixing(double value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, fullMoveSize, marketNoise, noiseDecay,
                marketQuorumFraction, marketWindowTicks, meetingSource, villagers, value, tradeWeight, witnessWeight);
    }

    /**
     * How many neighbourhoods the village is divided into, about one per seven villagers.
     *
     * <p>Derived rather than given, because it is not a free choice: a village is as
     * clustered as its size makes it, and a count picked for twenty would mean something
     * different in a village of six. Never fewer than one, so a hamlet is simply one
     * neighbourhood and behaves exactly as it did before.
     */
    public int neighbourhoods() {
        return Math.max(1, Math.round(villagers / PER_NEIGHBOURHOOD));
    }

    public int marketQuorum() {
        return Math.max(2, Math.min(villagers,
                (int) Math.round(marketQuorumFraction * villagers)));
    }

    /** The same settings with the market remembering its traders for longer. */
    public Params withMarketWindowTicks(int value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, fullMoveSize, marketNoise, noiseDecay,
                marketQuorumFraction, value, meetingSource, villagers, mixing, tradeWeight, witnessWeight);
    }

    /** The same settings for a village of a different size. */
    public Params withVillagers(int value) {
        return new Params(tellThreshold, repeatWeight, contradictionFactor, dailyDecay,
                forgetThreshold, mutationChance, plantedConfidence, basePrice, priceSensitivity,
                observationWeight, observationThreshold, fullMoveSize, marketNoise, noiseDecay,
                marketQuorumFraction, marketWindowTicks, meetingSource, value, mixing, tradeWeight, witnessWeight);
    }

    private static void requireFraction(double value, String name) {
        if (!(value >= 0 && value <= 1)) {
            throw new IllegalArgumentException(name + " must be between 0 and 1, was " + value);
        }
    }
}
