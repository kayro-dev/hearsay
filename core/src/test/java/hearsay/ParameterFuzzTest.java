package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Random settings, random seeds, and only the things that must hold whatever the knobs say.
 *
 * <p>Every sweep in EXPERIMENTS.md runs the defaults or a neighbourhood of them, which
 * leaves whole paths through the code unreached. E29 found a rumor born of the market price
 * had no family registered in {@link RumorStats}, so the first time one was told on the
 * report threw; it had been unreachable for as long as the telling threshold kept observed
 * beliefs from ever being repeated. No sweep found it and no test caught it. It surfaced
 * because a parameter moved.
 *
 * <p>So this asserts nothing about behaviour — no rates, no bands, nothing that tuning is
 * allowed to change. Only that the thing does not throw, that confidences are confidences,
 * that every belief points at a rumor that exists, and that the log still replays exactly.
 */
class ParameterFuzzTest {

    private static final int RUNS = 120;
    private static final int TICKS = 120;
    private static final long FUZZ_SEED = 424242;

    /** Fixed, so a failure is reproducible and reports the settings that caused it. */
    private final Random fuzz = new Random(FUZZ_SEED);

    private Params randomParams() {
        return new Params(
                fraction(), fraction(), fraction(),
                // Decay of zero forgets everything nightly and one never forgets; both are
                // legal and both are worth running.
                fraction(), fraction(), fraction(), fraction(),
                1 + fuzz.nextInt(500),          // basePrice
                fuzz.nextDouble() * 3,          // priceSensitivity
                fraction(), fraction(),
                0.01 + fuzz.nextDouble(),       // fullMoveSize, must be positive
                fraction(), fraction(), fraction(),
                fuzz.nextInt(20),               // marketWindowTicks
                MeetingSource.SIMULATED,
                2 + fuzz.nextInt(Simulation.MOST_VILLAGERS - 1),
                fraction(),                     // mixing
                fraction(),                     // tradeWeight
                fraction(),                     // witnessWeight
                fraction(),                     // checkWeight
                fraction());                    // emptyEvidence
    }

    private double fraction() {
        return fuzz.nextDouble();
    }

    @Test
    void anySettingsAtAllProduceARunThatHoldsTogether() {
        for (int run = 0; run < RUNS; run++) {
            Params params = randomParams();
            long seed = fuzz.nextLong();
            String where = "seed " + seed + " with " + params;

            Claim claim = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);
            List<Input> inputs = new ArrayList<>();
            inputs.add(new PlantRumor(1, claim, 1 + fuzz.nextInt(3),
                    fuzz.nextInt(params.villagers())));
            // Somebody selling diamonds, witnessed by a random handful. A trade reaches
            // code no headless sweep ever runs, which is exactly where the last bug lived.
            for (int trade = 0; trade < fuzz.nextInt(4); trade++) {
                int trader = fuzz.nextInt(params.villagers());
                java.util.NavigableSet<Integer> watching = new java.util.TreeSet<>();
                watching.add(trader);
                for (int extra = 0; extra < fuzz.nextInt(4); extra++) {
                    watching.add(fuzz.nextInt(params.villagers()));
                }
                inputs.add(new PlayerTraded(2 + fuzz.nextInt(TICKS - 2), trader,
                        1 + fuzz.nextInt(64), 1 + fuzz.nextInt(64), watching));
            }
            // Villagers looking at what the village has, which reaches the only rule in
            // the model that can lower a confidence rather than raise it.
            for (int look = 0; look < fuzz.nextInt(6); look++) {
                inputs.add(new RealityChecked(2 + fuzz.nextInt(TICKS - 2),
                        fuzz.nextInt(params.villagers()), Simulation.DIAMOND, fuzz.nextInt(80)));
            }
            inputs.sort(java.util.Comparator.comparingLong(Input::tick));

            Run executed = assertDoesNotThrow(
                    () -> Run.execute(seed, params, inputs, TICKS), where);

            everyBeliefIsABeliefInSomething(executed.finalState(), where);
            assertDoesNotThrow(() -> MarketStats.of(executed.log(), claim), where);
            // The report that E29 found broken. It walks rumor lineage, so it reaches
            // paths the simulation itself can leave alone.
            assertDoesNotThrow(() -> RumorStats.of(executed.log()), where);
            assertEquals(executed.finalState(), Simulation.replay(executed.log()),
                    "the log did not replay to the world it described: " + where);
        }
    }

    private static void everyBeliefIsABeliefInSomething(WorldState world, String where) {
        for (Villager villager : world.villagers().values()) {
            for (Belief belief : villager.beliefs().values()) {
                assertTrue(belief.confidence() >= 0 && belief.confidence() <= 1,
                        villager + " is " + belief.confidence() + " sure: " + where);
                assertNotNull(assertDoesNotThrow(() -> world.rumor(belief.rumorId()), where),
                        villager + " believes rumor " + belief.rumorId()
                                + ", which does not exist: " + where);
                assertFalse(belief.chain().contains(villager.id()),
                        villager + " is in their own chain: " + where);
            }
        }
    }

    @Test
    void theFuzzActuallyReachesTheInterestingPaths() {
        // A fuzz test that never makes anyone read the price, or never gets a rumor told,
        // would pass for the wrong reason. E29's bug needed both at once.
        Claim claim = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);
        boolean sawObservation = false;
        boolean sawObservationToldOn = false;

        for (int run = 0; run < RUNS && !sawObservationToldOn; run++) {
            Params params = randomParams();
            Run executed = Run.execute(fuzz.nextLong(), params,
                    List.of(new PlantRumor(1, claim, 1, 0)), TICKS);

            List<Integer> observed = new ArrayList<>();
            for (Event event : executed.log()) {
                if (event instanceof PriceObserved read) {
                    sawObservation = true;
                    observed.add(read.rumorId());
                }
                if (event instanceof RumorTold told && observed.contains(told.toldRumorId())) {
                    sawObservationToldOn = true;
                }
            }
        }

        assertTrue(sawObservation, "no villager ever read anything into the price");
        assertTrue(sawObservationToldOn,
                "no rumor born of the price was ever told on, which is the path E29 found broken");
    }
}
