package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Villagers live and work in one part of the village, so they meet the same few people
 * over and over. E22 measured a real village meeting 2.1 to 2.5 distinct people in two
 * days where the perfectly mixed model met 4.2, and E23 fitted the model to that.
 */
class NeighbourhoodTest {

    private static final int TICKS = 200;
    private static final int WINDOW = 8; // two days, at four ticks to the day
    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    private static List<Event> village(double mixing) {
        return Run.execute(42, Params.defaults().withMixing(mixing),
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, 0)), TICKS).log();
    }

    /** How many different people the average villager meets in a two-day window. */
    private static double distinctPartnersPerWindow(List<Event> log) {
        Map<Integer, List<long[]>> met = new TreeMap<>();
        for (Event event : log) {
            if (event instanceof VillagersMet m) {
                met.computeIfAbsent(m.a(), k -> new ArrayList<>()).add(new long[]{m.tick(), m.b()});
                met.computeIfAbsent(m.b(), k -> new ArrayList<>()).add(new long[]{m.tick(), m.a()});
            }
        }
        double total = 0;
        int windows = 0;
        for (List<long[]> theirs : met.values()) {
            for (long start = 1; start + WINDOW <= TICKS; start += WINDOW) {
                Set<Long> partners = new TreeSet<>();
                for (long[] row : theirs) {
                    if (row[0] >= start && row[0] < start + WINDOW) {
                        partners.add(row[1]);
                    }
                }
                total += partners.size();
                windows++;
            }
        }
        return total / windows;
    }

    private static long meetings(List<Event> log) {
        return log.stream().filter(e -> e instanceof VillagersMet).count();
    }

    @Test
    void aClusteredVillageMeetsFewerDifferentPeopleThanAMixedOne() {
        double mixed = distinctPartnersPerWindow(village(1.0));
        double clustered = distinctPartnersPerWindow(village(Params.MIXING));

        assertTrue(mixed > 3.5, "a perfectly mixed village should get about, was " + mixed);
        assertTrue(clustered < 2.5,
                "the whole point of neighbourhoods is meeting the same few people, but the "
                        + "average villager still met " + clustered + " different people in two days");
    }

    @Test
    void aPerfectlyMixedVillageIsExactlyTheVillageThatHadNoNeighbourhoods() {
        // Pinned, not derived. Neighbourhoods are drawn from a stream of their own and the
        // village-wide pool keeps id order, so a village at mixing 1.0 shuffles the same
        // list with the same generator it always did. If this number ever moves, some seed
        // that was recorded before E23 no longer replays to the village it described.
        assertEquals(1137, meetings(village(1.0)),
                "mixing at 1.0 must leave every seed recorded before neighbourhoods alone");
    }

    @Test
    void everyVillagerLivesInOneOfTheNeighbourhoodsTheVillageSizeAllowsFor() {
        Params params = Params.defaults();
        WorldState world = Simulation.replay(village(Params.MIXING));

        for (Villager villager : world.villagers().values()) {
            assertTrue(villager.neighbourhood() >= 0 && villager.neighbourhood() < params.neighbourhoods(),
                    villager + " lives in neighbourhood " + villager.neighbourhood()
                            + ", but there are only " + params.neighbourhoods());
        }
    }

    @Test
    void aHamletIsOneNeighbourhoodAndBehavesAsItAlwaysDid() {
        // Derived from size, so a village too small to have districts does not get any.
        assertEquals(1, Params.defaults().withVillagers(3).neighbourhoods());
    }
}
