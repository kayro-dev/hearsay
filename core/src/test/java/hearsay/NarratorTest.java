package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class NarratorTest {

    @Test
    void namesComeFromTheLogNotTheWorldState() {
        Narrator narrator = new Narrator();
        narrator.narrate(new VillagerCreated(1, 0, "Mira", new Traits(0.5, 0.5, 0.5)));
        narrator.narrate(new VillagerCreated(1, 1, "Bo", new Traits(0.5, 0.5, 0.5)));

        assertEquals("Day 2, morning: Mira meets Bo at the well.",
                narrator.narrate(new VillagersMet(5, 0, 1, Spot.WELL)).orElseThrow());
    }

    @Test
    void dayAndPartComeFromTheTick() {
        Narrator narrator = new Narrator();
        assertTrue(narrator.narrate(new VillagerCreated(1, 0, "Mira", new Traits(0, 0, 0)))
                .orElseThrow().startsWith("Day 1, morning: "));
        assertTrue(narrator.narrate(new VillagersMet(4, 0, 1, Spot.HOME))
                .orElseThrow().startsWith("Day 1, night: "));
        assertTrue(narrator.narrate(new VillagersMet(6, 0, 1, Spot.MARKET))
                .orElseThrow().startsWith("Day 2, midday: "));
    }

    @Test
    void movesAndPricesAreNotNarratedYet() {
        Narrator narrator = new Narrator();
        assertTrue(narrator.narrate(new VillagerMoved(1, 0, Spot.WELL)).isEmpty());
        assertTrue(narrator.narrate(new PriceChanged(1, 3)).isEmpty());
    }

    @Test
    void aWholeRunNarratesWithEveryVillagerNamed() {
        Simulation sim = new Simulation(42);
        sim.run(8); // two days

        List<String> lines = new Narrator().narrate(sim.log());

        assertFalse(lines.isEmpty());
        for (String line : lines) {
            assertFalse(line.contains("villager "), "every name should be known: " + line);
        }
    }
}
