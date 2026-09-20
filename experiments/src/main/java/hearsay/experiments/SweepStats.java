package hearsay.experiments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns per-seed outcomes into the summary one grid cell reports. Kept apart from the
 * runner so the arithmetic the tuning decision rests on can be tested directly.
 */
public final class SweepStats {

    private SweepStats() {
    }

    /** More than this many believers out of the village counts as overshooting. */
    public static final int OVERSHOOT_ABOVE = 15;

    /** What one seed did under one combination of settings. */
    public record SeedOutcome(long seed, int planter, int peakHeard, int peakBelieves,
                              int daysWithBeliever, boolean reachedHalfBelieves) {}

    /** What a whole grid cell did, across its seeds. */
    public record Summary(double dailyDecay, double tellThreshold, int seeds,
                          double meanPeakBelieves, int p10PeakBelieves, int p90PeakBelieves,
                          double shareReachingHalf, double meanDaysWithBeliever,
                          double shareOvershooting, double meanPeakHeard) {}

    public static Summary summarise(double dailyDecay, double tellThreshold,
                                    List<SeedOutcome> outcomes) {
        if (outcomes.isEmpty()) {
            throw new IllegalArgumentException("no seeds were run for this combination");
        }

        List<Integer> peaks = new ArrayList<>();
        double totalPeak = 0;
        double totalDays = 0;
        double totalHeard = 0;
        int reachedHalf = 0;
        int overshot = 0;

        for (SeedOutcome outcome : outcomes) {
            peaks.add(outcome.peakBelieves());
            totalPeak += outcome.peakBelieves();
            totalDays += outcome.daysWithBeliever();
            totalHeard += outcome.peakHeard();
            if (outcome.reachedHalfBelieves()) {
                reachedHalf++;
            }
            if (outcome.peakBelieves() > OVERSHOOT_ABOVE) {
                overshot++;
            }
        }
        Collections.sort(peaks);

        int seeds = outcomes.size();
        return new Summary(dailyDecay, tellThreshold, seeds,
                totalPeak / seeds,
                percentile(peaks, 0.10),
                percentile(peaks, 0.90),
                reachedHalf / (double) seeds,
                totalDays / seeds,
                overshot / (double) seeds,
                totalHeard / seeds);
    }

    /**
     * Nearest-rank percentile: the smallest value at or below which the given share of the
     * sorted sample falls. No interpolation, so every figure reported is a count that some
     * seed actually produced.
     */
    public static int percentile(List<Integer> sorted, double share) {
        if (sorted.isEmpty()) {
            throw new IllegalArgumentException("no values");
        }
        int rank = (int) Math.ceil(share * sorted.size());
        return sorted.get(Math.min(sorted.size() - 1, Math.max(0, rank - 1)));
    }
}
