package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Holds the shipped {@link Params#defaults()} to the behaviour they were chosen for.
 *
 * <p>Every other test asks whether a rule was followed. These ask whether the village still
 * does the thing the whole project is about: a planted rumor usually turns into a bubble,
 * and a village nobody lied to does not. A change that quietly retunes the model will pass
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

    private static final double AT_LEAST = 0.50;
    private static final double AT_MOST = 0.85;

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
    void aPlantedRumorTurnsIntoABubbleInMostSeedsButNotAllOfThem() {
        int halfBelieving = 0;
        for (long seed = FIRST_SEED; seed < FIRST_SEED + SEEDS; seed++) {
            if (runVillage(seed, aRumorFor(seed)).reachedHalfBelieving()) {
                halfBelieving++;
            }
        }

        double rate = halfBelieving / (double) SEEDS;
        assertTrue(rate >= AT_LEAST,
                "the loop has gone quiet: half the village believed in only "
                        + halfBelieving + " of " + SEEDS + " seeds");
        assertTrue(rate <= AT_MOST,
                "the loop has run away: half the village believed in "
                        + halfBelieving + " of " + SEEDS + " seeds");
    }

    @Test
    void aVillageNobodyLiedToNeverBursts() {
        for (long seed = FIRST_SEED; seed < FIRST_SEED + SEEDS; seed++) {
            assertTrue(runVillage(seed, List.of()).bubble().isEmpty(),
                    "seed " + seed + " panicked on its own, with nothing planted");
        }
    }
}
