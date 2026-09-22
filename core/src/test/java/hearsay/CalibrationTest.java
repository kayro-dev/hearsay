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
 * <p>The seeds are 1001 to 1100, held back from every sweep in EXPERIMENTS.md, so this
 * measures the settings rather than the seeds they were fitted on. E5 recorded 65%
 * half-believing and 94% bursting on exactly these seeds.
 *
 * <p>The bounds are wide on purpose. `observationWeight` moves the rate by twenty points
 * or more for a change of 0.05, so a band tight enough to pin the current figure would
 * break on any deliberate retune, and one this wide still catches the loop breaking or
 * running away.
 */
class CalibrationTest {

    private static final long FIRST_SEED = 1001;
    private static final int SEEDS = 100;
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
     * whole interval, not its point. 43 of 100 on these seeds gives [34%, 53%]. A bare
     * "between 25 and 60" would have passed a 59% whose interval ran to 68%.
     *
     * <p>Demonstrated rather than merely not contradicted because this is the figure the
     * README states, and a stated figure is a claim. The seeds are fixed and the simulation
     * deterministic, so the test never flickers: the interval says what these hundred seeds
     * show about the model, not how much this run might wobble.
     */
    private static final Target.Band A_LIE_BURSTS = Target.demonstrates(
            "a lie bursts the price within 30 days", 0.25, 0.60, 100);

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
}
