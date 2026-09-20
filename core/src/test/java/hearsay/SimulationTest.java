package hearsay;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SimulationTest {
    @Test
    void sameSeedGivesSameState() {
        Simulation a = new Simulation(42);
        Simulation b = new Simulation(42);
        a.run(10_000);
        b.run(10_000);
        assertEquals(a.state(), b.state());
    }

    @Test
    void differentSeedsGiveDifferentEventLogs() {
        Simulation a = new Simulation(42);
        Simulation b = new Simulation(7);
        a.run(10_000);
        b.run(10_000);
        // Compare the logs, not the final state: two different walks can land on
        // the same price by chance, but they cannot produce the same 10,000 events.
        assertNotEquals(a.log(), b.log());
    }

    @Test
    void replayingTheLogRebuildsTheExactState() {
        Simulation sim = new Simulation(42);
        sim.run(10_000);
        WorldState replayed = Simulation.replay(sim.log());
        assertEquals(sim.state(), replayed);
    }
}
