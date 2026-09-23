package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Holds the shipped {@link Params#defaults()} to the behaviour they were chosen for.
 *
 * <p>Every other test asks whether a rule was followed. These ask whether the village still
 * does the thing the whole project is about: a planted rumor usually turns into a bubble,
 * and a village nobody lied to almost never does. A change that quietly retunes the model will pass
 * every invariant and fail here.
 *
 * <p>The seeds start at 1001, held back from every sweep in EXPERIMENTS.md, so this
 * measures the settings rather than the seeds they were fitted on. E5 recorded 65%
 * half-believing and 94% bursting on 1001-1100; the lie's check has run on 1001-1200
 * since E43.
 *
 * <p>The bounds are wide on purpose. `observationWeight` moves the rate by twenty points
 * or more for a change of 0.05, so a band tight enough to pin the current figure would
 * break on any deliberate retune, and one this wide still catches the loop breaking or
 * running away.
 */
class CalibrationTest {

    private static final long FIRST_SEED = 1001;
    private static final int SEEDS = 200;
    private static final int TICKS = 200; // 50 days
    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    // Re-derived in E23 on the clustered model. The old band held half-believing between
    // 50% and 85%, which a perfectly mixed village reached and a real one never does: once
    // the model meets as few people as a played village, half-believing falls to 1%. What
    // survives as a measure of the loop working is how often the lie bursts the price,
    // measured at 39% on these seeds.
    /**
     * How long after a lie a bubble may still be laid at its door.
     *
     * <p>A month. Beyond that the village has had time to talk itself into anything, and
     * E38 measured the difference: 60% of long runs bubble somewhere, 38% within thirty
     * days of the lie. A claim with no window is a claim about the village's whole life.
     */
    private static final int WITHIN_DAYS = 30;

    /**
     * The lie's thirty-day burst rate has to be <em>demonstrated</em> inside 25-60%: its
     * whole interval, not its point. A bare "between 25 and 60" would have passed a 59%
     * whose interval ran to 68%.
     *
     * <p>Demonstrated rather than merely not contradicted, because not contradicted cannot
     * catch a loop that has gone quiet: E43 found it missed a retune that halved the burst
     * rate more than half the time.
     *
     * <p><strong>On two hundred seeds, not a hundred, and E43 is why.</strong> The seeds are
     * fixed and the simulation deterministic, so this never flickers between runs — but any
     * change that keeps the model's behaviour while re-rolling its dice (adding a random
     * stream did exactly that in stage 2) hands it a fresh sample. On a hundred seeds a
     * demonstrated band this narrow failed an unchanged model <strong>16.8%</strong> of the
     * time, measured by resampling five thousand calibration runs from the real model's own
     * runs; the estimate had to land between about 34% and 50% when the true rate is 38%.
     * On two hundred it fails 2.0%. Together the three checks still catch every retune
     * tried, every time, but not all by this one: this check catches loops quietened to a
     * 13% or 18% burst rate and the runaway at 63%; the runaway at 47% sits inside the band
     * and is caught by the lifetime check below instead. Sharing the
     * confidence across the three checks, as the goods gate does, was tried and made it
     * worse, 33.6%: for a demonstrated band a wider interval fails more, not less.
     */
    private static final Target.Band A_LIE_BURSTS = Target.demonstrates(
            "a lie bursts the price within 30 days", 0.25, 0.60, SEEDS);

    /**
     * Villages nobody lied to, and how many of them may talk themselves into a bubble.
     *
     * <p>This used to demand none at all, which was a claim about a hundred seeds dressed
     * up as a law. Measured across nine hundred seeds the rate is about 0.33%: one in
     * 1001-1300, one in 2001-2200, one in 5000-5399. A village that panics unaided is rare,
     * not impossible, and a test that forbids it outright fails the first time an unlucky
     * seed is added to the set.
     *
     * <p>Three hundred seeds with a ceiling of four keeps the power that matters. At the
     * measured rate four or more happens about three times in a thousand, so it will not
     * cry wolf; if the rate ever climbed to 3%, this would catch it in 98 runs out of 100.
     */
    private static final int QUIET_SEEDS = 300;

    /**
     * Villages nobody lied to, demonstrated under 3% within a month. None of 300 does on
     * these seeds, and Wilson's interval on none-in-300 still runs to about 1.3%, so the
     * claim is "rare", never "never". It tolerates three bursts in 300 and fails at four,
     * one stricter than the bare count it replaced — which allowed four without saying
     * what four in 300 could and could not show.
     */
    private static final Target.Band QUIET_VILLAGES = Target.demonstrates(
            "villages nobody lied to bursting within 30 days", 0.0, 0.03, QUIET_SEEDS);

    /**
     * The same guarantee over a village's whole life, so the windowed check above is not
     * left guarding alone. Windowed to a month, a village nobody lied to almost never
     * bursts even when the loop is running away — E40 found that at observationWeight 0.45
     * the thirty-day check no longer fails at all — because a runaway loop still needs time
     * to talk itself into something. Over five hundred days it has that time.
     *
     * <p>Sixty villages of five hundred days each. At the defaults these give 0.100 [0.025,
     * 0.175] bursts per 100 village-days; at 0.35 they give 1.06 [0.72, 1.39], and at 0.45,
     * 1.34 [0.95, 1.72]. The ceiling sits between them, with room on both sides.
     *
     * <p>The length is fixed, and has to be. A rate per village-day is still not quite
     * independent of how long anyone watched: villages need a while to warm up before they
     * can burst unaided, so at 0.35 the rate over 250 days is half the rate over 500. At
     * the defaults the difference is inside the noise, but the figure is only ever quoted
     * with its length.
     */
    private static final int LONG_RUNS = 60;
    private static final int LONG_TICKS = 2000; // five hundred days
    private static final Target.Band BACKGROUND_RATE = Target.demonstrates(
            "villages nobody lied to bursting, per 100 village-days over 500 days",
            0.0, 0.5, LONG_RUNS);

    private static MarketStats runVillage(long seed, List<Input> inputs) {
        return MarketStats.of(
                Run.execute(seed, Params.defaults(), inputs, TICKS).log(), DIAMONDS_SCARCE);
    }

    private static List<Input> aRumorFor(long seed) {
        int planter = Run.execute(seed, Params.defaults(), List.of(), 1)
                .finalState().gossipiestVillager().id();
        return List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, planter));
    }

    @Test
    void aPlantedRumorBurstsThePriceInSomeSeedsButNotMost() {
        int burst = 0;
        for (long seed = FIRST_SEED; seed < FIRST_SEED + SEEDS; seed++) {
            // Within a month of the lie, not merely somewhere in the run. E38 found 60%
            // of long runs bubble at some point and only 38% within thirty days of being
            // lied to: the other twenty-two points are the village wandering on its own,
            // and crediting them to the lie flatters it.
            if (runVillage(seed, aRumorFor(seed)).bubbleWithin(1, WITHIN_DAYS).isPresent()) {
                burst++;
            }
        }

        Target.Verdict verdict = A_LIE_BURSTS.judge(Estimate.proportion(burst, SEEDS));
        assertTrue(verdict.passed(), "the model has been retuned: " + verdict);
    }

    @Test
    void villagesNobodyLiedToHardlyEverBurst() {
        List<Long> panicked = new ArrayList<>();
        for (long seed = FIRST_SEED; seed < FIRST_SEED + QUIET_SEEDS; seed++) {
            if (runVillage(seed, List.of()).bubbleWithin(1, WITHIN_DAYS).isPresent()) {
                panicked.add(seed);
            }
        }

        Target.Verdict verdict = QUIET_VILLAGES.judge(
                Estimate.proportion(panicked.size(), QUIET_SEEDS));
        assertTrue(verdict.passed(), "villages nobody lied to are bursting, at seeds "
                + panicked + ": " + verdict + ". E38 measured 0.19 per 100 village-days, "
                + "so this is the loop starting itself.");
    }

    @Test
    void villagesNobodyLiedToStayRareOverAWholeLifetime() {
        int[] bursts = new int[LONG_RUNS];
        for (int i = 0; i < LONG_RUNS; i++) {
            bursts[i] = MarketStats.of(
                    Run.execute(FIRST_SEED + i, Params.defaults(), List.of(), LONG_TICKS).log(),
                    DIAMONDS_SCARCE).bubbles().size();
        }
        Estimate rate = Estimate.ratePer(bursts, LONG_TICKS / 4.0, 100);

        Target.Verdict verdict = BACKGROUND_RATE.judge(rate);
        assertTrue(verdict.passed(), "villages nobody lied to are talking themselves into "
                + "bubbles over the long run: " + verdict);
    }

    /**
     * How often the burst check fails when the true thirty-day burst rate is {@code rate},
     * over two thousand fresh samples of the size the check really uses.
     */
    private static double failureRateAt(double rate) {
        java.util.Random draws = new java.util.Random(20260923);
        int failed = 0;
        for (int sample = 0; sample < 2000; sample++) {
            int bursts = 0;
            for (int seed = 0; seed < SEEDS; seed++) {
                bursts += draws.nextDouble() < rate ? 1 : 0;
            }
            failed += A_LIE_BURSTS.judge(Estimate.proportion(bursts, SEEDS)).passed() ? 0 : 1;
        }
        return failed / 2000.0;
    }

    @Test
    void theBurstCheckRarelyFailsAnUnchangedModelAndCatchesTheRetunesItIsFor() {
        // The rates are the real model's, pooled over 3,000 lies on fresh seeds each (E43).
        // Pinned to the check itself, so shrinking it back to a hundred seeds fails here:
        // at a hundred an unchanged model fails about one time in six.
        double unchanged = failureRateAt(0.384);
        assertTrue(unchanged < 0.05, "the burst check fails an unchanged model "
                + unchanged * 100 + "% of the time");

        // Its job: loops that have gone quiet, and a runaway far enough to leave the band.
        // A runaway to 47% stays inside 25-60% and is the lifetime check's to catch, which
        // it does every time (E43); claiming it here would credit this check with another's
        // work.
        for (double retuned : new double[] {0.131, 0.176, 0.633}) {
            assertTrue(failureRateAt(retuned) > 0.95, "a model retuned to a " + retuned * 100
                    + "% burst rate should fail the check almost always, but failed only "
                    + failureRateAt(retuned) * 100 + "% of the time");
        }
    }
}
