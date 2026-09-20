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
    void differentSeedsGiveDifferentStates() {
        Simulation a = new Simulation(42);
        Simulation b = new Simulation(7);
        a.run(10_000);
        b.run(10_000);
        assertNotEquals(a.state(), b.state());
    }

    @Test
    void replayingTheLogRebuildsTheExactState() {
        Simulation sim = new Simulation(42);
        sim.run(10_000);
        WorldState replayed = Simulation.replay(sim.log());
        assertEquals(sim.state(), replayed);
    }
}
