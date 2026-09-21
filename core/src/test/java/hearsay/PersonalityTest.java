package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Names that tell villagers apart, rationed so they keep meaning something. */
class PersonalityTest {

    private static WorldState villageOf(long seed, int villagers) {
        return Simulation.replay(Run.execute(seed,
                Params.defaults().withVillagers(villagers), List.of(), 1).log());
    }

    @Test
    void namingNobodyChangesNothing() {
        // The same promise BeliefReport makes, and the reason neither cost an experiment a
        // re-run: no decision anywhere consults a label.
        Run run = Run.execute(42, Params.defaults(), List.of(), 40);
        WorldState world = run.finalState();
        WorldState untouched = Simulation.replay(run.log());

        Personality.of(world);

        assertEquals(untouched, world, "naming the village changed the village");
    }

    @Test
    void aVillageCarriesAboutOneNamePerFivePeople() {
        assertEquals(4, Personality.labelsFor(20));
        assertEquals(1, Personality.labelsFor(8));
        assertEquals(1, Personality.labelsFor(4));
        assertEquals(1, Personality.labelsFor(2), "even a hamlet has somebody who stands out");
    }

    @Test
    void aVillageOfTwentyIsNotCoveredInNames() {
        // The first attempt named 78% of every village, which is a name on nobody. This is
        // the measurement that caught it, kept as a test so it cannot come back.
        for (long seed = 1001; seed < 1021; seed++) {
            Map<Integer, String> named = Personality.of(villageOf(seed, 20));
            assertTrue(named.size() <= 4,
                    "seed " + seed + " named " + named.size() + " of 20: " + named);
        }
    }

    @Test
    void theTownCrierIsNeverCrowdedOut() {
        // Whoever talks most is the one the player needs to find, so they are named before
        // any miser or sceptic gets a look in.
        for (long seed = 1001; seed < 1041; seed++) {
            WorldState world = villageOf(seed, 20);
            Map<Integer, String> named = Personality.of(world);
            int loudest = world.gossipiestVillager().id();

            if (world.villager(loudest).traits().gossip() >= 0.70) {
                assertEquals("the Town Crier", named.get(loudest),
                        "seed " + seed + " gave the loudest villager " + named.get(loudest));
            }
        }
    }

    @Test
    void aNameBelongsToTheMostExtremeVillagerAndNobodyElse() {
        WorldState world = villageOf(1001, 20);
        Map<Integer, String> named = Personality.of(world);

        assertEquals(named.values().size(), named.values().stream().distinct().count(),
                "a village should not have two Town Criers: " + named);
        for (int id : named.keySet()) {
            assertEquals(1, named.keySet().stream().filter(other -> other == id).count(),
                    "a villager should answer to one name at most");
        }
    }

    @Test
    void aMildVillagerIsNotNamedForBeingOrdinary() {
        // The floor. A village where nobody is far from the middle should say so by saying
        // nothing, rather than crowning whoever is nearest to interesting.
        WorldState world = villageOf(1001, 20);
        Map<Integer, String> named = Personality.of(world);

        for (Map.Entry<Integer, String> entry : named.entrySet()) {
            Traits traits = world.villager(entry.getKey()).traits();
            double furthest = Math.max(Math.abs(traits.gossip() - 0.5),
                    Math.max(Math.abs(traits.credulity() - 0.5), Math.abs(traits.greed() - 0.5)));
            assertTrue(furthest >= 0.20,
                    entry.getValue() + " is not far enough from the middle to be called it");
        }
    }

    @Test
    void theSameVillageIsNamedTheSameWayTwice() {
        assertEquals(Personality.of(villageOf(1001, 20)), Personality.of(villageOf(1001, 20)));
    }
}
