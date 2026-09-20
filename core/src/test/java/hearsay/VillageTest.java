package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Invariants the village must hold every single tick. */
class VillageTest {

    private static final int TICKS = 400; // 100 days

    private static Simulation runVillage() {
        Simulation sim = new Simulation(42);
        sim.run(TICKS);
        return sim;
    }

    /** The log grouped by tick, each tick keeping its original event order. */
    private static Map<Long, List<Event>> byTick(List<Event> log) {
        Map<Long, List<Event>> ticks = new LinkedHashMap<>();
        for (Event event : log) {
            ticks.computeIfAbsent(event.tick(), t -> new ArrayList<>()).add(event);
        }
        return ticks;
    }

    /** Where everyone ended up on a given tick, read back out of that tick's move events. */
    private static Map<Integer, Spot> spotsOn(List<Event> tick) {
        Map<Integer, Spot> spots = new TreeMap<>();
        for (Event event : tick) {
            if (event instanceof VillagerMoved e) {
                spots.put(e.id(), e.spot());
            }
        }
        return spots;
    }

    @Test
    void theWholeVillageIsCreatedOnTheFirstTick() {
        Simulation sim = runVillage();

        List<VillagerCreated> created = new ArrayList<>();
        for (Event event : sim.log()) {
            if (event instanceof VillagerCreated e) {
                created.add(e);
            }
        }

        assertEquals(Simulation.VILLAGER_COUNT, created.size());
        assertEquals(Simulation.VILLAGER_COUNT, sim.state().villagers().size());
        for (VillagerCreated e : created) {
            assertEquals(1, e.tick(), "villagers are only born on the first tick");
        }
    }

    @Test
    void everyVillagerIsAtExactlyOneSpotEveryTick() {
        Simulation sim = runVillage();

        Set<Integer> everyone = new HashSet<>();
        for (int id = 0; id < Simulation.VILLAGER_COUNT; id++) {
            everyone.add(id);
        }

        for (Map.Entry<Long, List<Event>> tick : byTick(sim.log()).entrySet()) {
            List<Integer> moved = new ArrayList<>();
            for (Event event : tick.getValue()) {
                if (event instanceof VillagerMoved e) {
                    moved.add(e.id());
                }
            }
            assertEquals(Simulation.VILLAGER_COUNT, moved.size(),
                    "tick " + tick.getKey() + " should move every villager exactly once");
            assertEquals(everyone, new HashSet<>(moved),
                    "tick " + tick.getKey() + " should move each villager, with no repeats");
        }
    }

    @Test
    void meetingsPairTwoDifferentVillagersStandingAtTheSameSpot() {
        Simulation sim = runVillage();

        int meetings = 0;
        for (Map.Entry<Long, List<Event>> tick : byTick(sim.log()).entrySet()) {
            Map<Integer, Spot> spots = spotsOn(tick.getValue());
            for (Event event : tick.getValue()) {
                if (event instanceof VillagersMet e) {
                    meetings++;
                    assertNotEquals(e.a(), e.b(), "a villager cannot meet themselves");
                    assertEquals(e.spot(), spots.get(e.a()), "villager " + e.a() + " was elsewhere");
                    assertEquals(e.spot(), spots.get(e.b()), "villager " + e.b() + " was elsewhere");
                }
            }
        }
        assertTrue(meetings > 0, "the village should actually be meeting");
    }

    @Test
    void nobodyMeetsTwiceInTheSameTick() {
        Simulation sim = runVillage();

        for (Map.Entry<Long, List<Event>> tick : byTick(sim.log()).entrySet()) {
            Set<Integer> alreadyMet = new HashSet<>();
            for (Event event : tick.getValue()) {
                if (event instanceof VillagersMet e) {
                    assertTrue(alreadyMet.add(e.a()),
                            "villager " + e.a() + " met twice on tick " + tick.getKey());
                    assertTrue(alreadyMet.add(e.b()),
                            "villager " + e.b() + " met twice on tick " + tick.getKey());
                }
            }
        }
    }

    @Test
    void nightSendsNearlyEveryoneHomeAndMiddaySendsNobody() {
        Simulation sim = runVillage();

        int nightHome = 0;
        int nightTotal = 0;
        int middayHome = 0;
        int middayTotal = 0;

        for (Event event : sim.log()) {
            if (!(event instanceof VillagerMoved e)) {
                continue;
            }
            switch (DayPart.of(e.tick())) {
                case NIGHT -> {
                    nightTotal++;
                    if (e.spot() == Spot.HOME) nightHome++;
                }
                case MIDDAY -> {
                    middayTotal++;
                    if (e.spot() == Spot.HOME) middayHome++;
                }
                default -> { }
            }
        }

        assertTrue(nightTotal > 0 && middayTotal > 0, "the run should cover both day parts");
        assertTrue(nightHome / (double) nightTotal > 0.9,
                "nearly everyone should be home at night, was " + nightHome + "/" + nightTotal);
        assertEquals(0, middayHome, "nobody is home at midday: that spot has weight 0");
    }
}
