package hearsay;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SimulationTest {

    private static final int TICKS = 2_000; // 500 days

    @Test
    void sameSeedGivesSameState() {
        Simulation a = new Simulation(42);
        Simulation b = new Simulation(42);
        a.run(TICKS);
        b.run(TICKS);
        assertEquals(a.state(), b.state());
    }

    @Test
    void differentSeedsGiveDifferentEventLogs() {
        Simulation a = new Simulation(42);
        Simulation b = new Simulation(7);
        a.run(TICKS);
        b.run(TICKS);
        // Compare the logs, not the final state: two different walks can land on the
        // same price by chance, but they cannot produce the same event sequence.
        assertNotEquals(a.log(), b.log());
    }

    @Test
    void replayingTheLogRebuildsTheExactState() {
        Simulation sim = new Simulation(42);
        sim.run(TICKS);

        WorldState replayed = Simulation.replay(sim.log());

        // Check the world being compared is actually populated, so this cannot pass by
        // comparing two empty villages.
        assertEquals(Simulation.VILLAGER_COUNT, replayed.villagers().size());
        assertEquals(sim.state(), replayed);
    }

    @Test
    void replayRebuildsEveryVillagerDownToTheirTraitsAndSpot() {
        Simulation sim = new Simulation(42);
        sim.run(TICKS);

        WorldState replayed = Simulation.replay(sim.log());

        for (int id = 0; id < Simulation.VILLAGER_COUNT; id++) {
            Villager original = sim.state().villager(id);
            Villager rebuilt = replayed.villager(id);
            assertEquals(original.name(), rebuilt.name());
            assertEquals(original.traits(), rebuilt.traits());
            assertEquals(original.spot(), rebuilt.spot());
        }
    }
}
