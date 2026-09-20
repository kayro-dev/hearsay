package hearsay.experiments;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The arithmetic the tuning decision rests on. */
class SweepStatsTest {

    private static SweepStats.SeedOutcome outcome(long seed, int peakBelieves, int days, boolean half) {
        return new SweepStats.SeedOutcome(seed, 0, 20, peakBelieves, days, half);
    }

    @Test
    void percentilesAreValuesSomeSeedActuallyProduced() {
        List<Integer> sorted = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            sorted.add(i);
        }
        // Nearest rank: the 10th percentile of 1..50 is the 5th value, the 90th the 45th.
        assertEquals(5, SweepStats.percentile(sorted, 0.10));
        assertEquals(45, SweepStats.percentile(sorted, 0.90));
        assertEquals(1, SweepStats.percentile(sorted, 0.0));
        assertEquals(50, SweepStats.percentile(sorted, 1.0));
    }

    @Test
    void percentilesSurviveASingleSeed() {
        assertEquals(7, SweepStats.percentile(List.of(7), 0.10));
        assertEquals(7, SweepStats.percentile(List.of(7), 0.90));
    }

    @Test
    void sharesAreCountedOutOfTheSeedsRun() {
        List<SweepStats.SeedOutcome> outcomes = List.of(
                outcome(1, 2, 3, false),
                outcome(2, 16, 9, true),   // overshoots and reaches half
                outcome(3, 10, 7, true),   // reaches half, does not overshoot
                outcome(4, 0, 0, false));

        SweepStats.Summary summary = SweepStats.summarise(0.9, 0.3, outcomes);

        assertEquals(4, summary.seeds());
        assertEquals(7.0, summary.meanPeakBelieves(), 1e-9);   // (2+16+10+0)/4
        assertEquals(0.5, summary.shareReachingHalf(), 1e-9);
        assertEquals(0.25, summary.shareOvershooting(), 1e-9); // only 16 is above 15
        assertEquals(4.75, summary.meanDaysWithBeliever(), 1e-9);
    }

    @Test
    void overshootingIsStrictlyAboveTheThreshold() {
        SweepStats.Summary atTheLine = SweepStats.summarise(0.9, 0.3,
                List.of(outcome(1, SweepStats.OVERSHOOT_ABOVE, 5, true)));
        SweepStats.Summary overTheLine = SweepStats.summarise(0.9, 0.3,
                List.of(outcome(1, SweepStats.OVERSHOOT_ABOVE + 1, 5, true)));

        assertEquals(0.0, atTheLine.shareOvershooting(), 1e-9);
        assertEquals(1.0, overTheLine.shareOvershooting(), 1e-9);
    }

    @Test
    void aCombinationWithNoSeedsIsAnError() {
        assertThrows(IllegalArgumentException.class,
                () -> SweepStats.summarise(0.9, 0.3, List.of()));
    }
}
