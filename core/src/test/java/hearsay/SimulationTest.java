package hearsay;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SimulationTest {
    @Test
    void sameSeedGivesSameResult() {
        Simulation a = new Simulation(42);
        Simulation b = new Simulation(42);
        a.run(10_000);
        b.run(10_000);
        assertEquals(a.state(), b.state());
    }

    @Test
    void differentSeedsGiveDifferentResults() {
        Simulation a = new Simulation(42);
        Simulation b = new Simulation(7);
        a.run(10_000);
        b.run(10_000);
        assertNotEquals(a.state(), b.state());
    }
}