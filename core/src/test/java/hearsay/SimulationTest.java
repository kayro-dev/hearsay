package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationTest {

    private static final int TICKS = 2_000; // 500 days
    private static final Claim DIAMONDS_SCARCE = new Claim("diamond", ClaimType.SCARCE);

    /**
     * A rumor every ten days, alternating between a claim and its opposite, so the
     * village always has live beliefs and the contradiction path gets exercised. A single
     * planted rumor fades out of everyone long before tick 2000, which would leave the
     * replay comparison below comparing empty belief maps.
     */
    private static List<Input> aSteadyDripOfRumors() {
        List<Input> inputs = new ArrayList<>();
        int planting = 0;
        for (long tick = 1; tick <= TICKS; tick += 40, planting++) {
            Claim claim = planting % 2 == 0 ? DIAMONDS_SCARCE : DIAMONDS_SCARCE.opposite();
            inputs.add(new PlantRumor(tick, claim, 1, planting % Simulation.VILLAGER_COUNT));
        }
        return inputs;
    }

    private static Simulation runWithRumor(long seed) {
        Simulation sim = new Simulation(seed, Params.defaults(), aSteadyDripOfRumors());
        sim.run(TICKS);
        return sim;
    }

    private static int totalBeliefs(WorldState state) {
        int count = 0;
        for (Villager villager : state.villagers().values()) {
            count += villager.beliefs().size();
        }
        return count;
    }

    @Test
    void sameSeedGivesSameState() {
        assertEquals(runWithRumor(42).state(), runWithRumor(42).state());
    }

    @Test
    void differentSeedsGiveDifferentEventLogs() {
        // Compare the logs, not the final state: two different runs can land on the same
        // price by chance, but they cannot produce the same event sequence.
        assertNotEquals(runWithRumor(42).log(), runWithRumor(7).log());
    }

    @Test
    void replayingTheLogRebuildsTheExactState() {
        Simulation sim = runWithRumor(42);

        WorldState replayed = Simulation.replay(sim.log());

        // Check the world being compared is actually populated, so this cannot pass by
        // comparing two empty villages.
        assertEquals(Simulation.VILLAGER_COUNT, replayed.villagers().size());
        assertFalse(replayed.rumors().isEmpty(), "the run should have produced rumors");
        assertTrue(totalBeliefs(replayed) > 0, "somebody should still believe something");
        assertEquals(sim.state(), replayed);
    }

    @Test
    void replayRebuildsEveryVillagerDownToTheirTraitsSpotAndBeliefs() {
        Simulation sim = runWithRumor(42);

        WorldState replayed = Simulation.replay(sim.log());

        for (int id = 0; id < Simulation.VILLAGER_COUNT; id++) {
            Villager original = sim.state().villager(id);
            Villager rebuilt = replayed.villager(id);
            assertEquals(original.name(), rebuilt.name());
            assertEquals(original.traits(), rebuilt.traits());
            assertEquals(original.spot(), rebuilt.spot());
            assertEquals(original.beliefs(), rebuilt.beliefs());
        }
    }

    @Test
    void replayRebuildsTheRumorFamilyTree() {
        Simulation sim = runWithRumor(42);

        WorldState replayed = Simulation.replay(sim.log());

        assertEquals(sim.state().rumors(), replayed.rumors());
        assertEquals(sim.state().nextRumorId(Good.DIAMOND), replayed.nextRumorId(Good.DIAMOND));
    }
}
