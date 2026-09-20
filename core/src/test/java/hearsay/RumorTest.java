package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** How rumors travel, and what must stay true while they do. */
class RumorTest {

    private static final int TICKS = 100; // 25 days: long enough to spread, short enough
                                          // that the rumor has not yet faded out of everyone
    private static final Claim DIAMONDS_SCARCE = new Claim("diamond", ClaimType.SCARCE);
    private static final long SEED = 42;

    /**
     * Villager 18 (Pim) is a talker on this seed, gossip 0.93. Villager 0 would be a poor
     * fixture: on this seed Mira's gossip is 0.05, and her confidence decays below the
     * telling threshold before she manages to mention it to anyone, so nothing spreads
     * and every test below would pass while proving nothing.
     */
    private static final int PLANTED_IN = 18;

    private static Simulation run(List<Input> inputs) {
        Simulation sim = new Simulation(SEED, Params.defaults(), inputs);
        sim.run(TICKS);
        return sim;
    }

    private static Simulation runWithRumor() {
        return run(List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, PLANTED_IN)));
    }

    /** The events that say where people are, as opposed to what they said. */
    private static List<Event> whereEveryoneWent(List<Event> log) {
        List<Event> physical = new ArrayList<>();
        for (Event event : log) {
            if (event instanceof VillagerMoved || event instanceof VillagersMet) {
                physical.add(event);
            }
        }
        return physical;
    }

    private static List<RumorTold> tellings(List<Event> log) {
        List<RumorTold> told = new ArrayList<>();
        for (Event event : log) {
            if (event instanceof RumorTold e) {
                told.add(e);
            }
        }
        return told;
    }

    /**
     * The counterfactual foundation. Planting a rumor must not move anybody, or a
     * counterfactual would differ for reasons that have nothing to do with the rumor.
     */
    @Test
    void plantingARumorChangesWhatIsSaidButNotWhereAnyoneWalks() {
        Simulation withRumor = runWithRumor();
        Simulation withoutRumor = run(List.of());

        assertEquals(whereEveryoneWent(withoutRumor.log()), whereEveryoneWent(withRumor.log()),
                "movement and meetings must be identical with and without the rumor");

        // ...and the rumor must actually have done something, or the comparison above is
        // comparing two identical runs and proves nothing.
        assertFalse(tellings(withRumor.log()).isEmpty(), "the rumor should have been told");
        assertTrue(tellings(withoutRumor.log()).isEmpty(), "nothing to tell without a rumor");
        assertNotEquals(withoutRumor.log(), withRumor.log());
    }

    @Test
    void rumorsOnlyTravelThroughMeetings() {
        Simulation sim = runWithRumor();

        Set<String> meetings = new HashSet<>();
        for (Event event : sim.log()) {
            if (event instanceof VillagersMet e) {
                meetings.add(pair(e.tick(), e.a(), e.b()));
            }
        }

        for (RumorTold told : tellings(sim.log())) {
            assertTrue(meetings.contains(pair(told.tick(), told.tellerId(), told.listenerId())),
                    "telling on tick " + told.tick() + " had no matching meeting");
        }
    }

    private static String pair(long tick, int a, int b) {
        return tick + ":" + Math.min(a, b) + "-" + Math.max(a, b);
    }

    @Test
    void confidenceAlwaysStaysBetweenZeroAndOne() {
        Simulation sim = runWithRumor();

        for (RumorTold told : tellings(sim.log())) {
            assertTrue(told.newConfidence() >= 0 && told.newConfidence() <= 1,
                    "confidence out of range: " + told.newConfidence());
        }
        for (Villager villager : sim.state().villagers().values()) {
            for (Belief belief : villager.beliefs().values()) {
                assertTrue(belief.confidence() >= 0 && belief.confidence() <= 1,
                        "belief out of range: " + belief);
            }
        }
    }

    @Test
    void everyRumorLeadsBackToAPlantedOne() {
        Simulation sim = runWithRumor();
        WorldState state = sim.state();

        assertTrue(state.rumors().size() > 1, "the run should have produced mutated rumors");

        // Only rumors that were actually planted may claim to be planted. Without this,
        // a rumor that grew in the telling could drop its parent and pass itself off as
        // the origin, and every chain below would still lead somewhere "planted".
        int plantedEvents = 0;
        for (Event event : sim.log()) {
            if (event instanceof RumorPlanted e) {
                plantedEvents++;
                assertTrue(state.rumor(e.rumorId()).isPlanted());
            }
            if (event instanceof RumorMutated e) {
                assertEquals(e.parentId(), state.rumor(e.rumorId()).parentId(),
                        "rumor " + e.rumorId() + " lost the parent it grew from");
            }
        }
        int plantedRumors = 0;
        for (Rumor rumor : state.rumors().values()) {
            if (rumor.isPlanted()) {
                plantedRumors++;
            }
        }
        assertEquals(plantedEvents, plantedRumors, "a rumor invented a planted ancestor");

        for (Rumor rumor : state.rumors().values()) {
            Rumor current = rumor;
            int steps = 0;
            while (!current.isPlanted()) {
                current = state.rumor(current.parentId());
                assertTrue(++steps <= state.rumors().size(), "parent chain loops: " + rumor);
            }
            assertEquals(rumor.claim(), current.claim(), "a rumor may grow, but not change subject");
        }
    }

    @Test
    void nobodyInventsABeliefFromNothing() {
        Simulation sim = runWithRumor();
        WorldState state = sim.state();

        int checked = 0;
        for (Villager villager : state.villagers().values()) {
            for (Belief belief : villager.beliefs().values()) {
                Rumor source = state.rumor(belief.rumorId()); // throws if it never existed
                assertTrue(state.rootOf(source).isPlanted());
                assertEquals(belief.claim(), source.claim());
                checked++;
            }
        }
        assertTrue(checked > 0, "somebody should believe something by now");
    }

    @Test
    void aGrownRumorIsNeverWeakerThanItsParent() {
        Simulation sim = runWithRumor();
        WorldState state = sim.state();

        for (Rumor rumor : state.rumors().values()) {
            if (!rumor.isPlanted()) {
                Rumor parent = state.rumor(rumor.parentId());
                assertTrue(rumor.severity() >= parent.severity(),
                        "rumor " + rumor.id() + " shrank in the telling");
                assertTrue(rumor.severity() <= Rumor.MAX_SEVERITY);
            }
        }
    }

    @Test
    void aRumorAtTheTopOfTheScaleStopsGrowing() {
        // Crank the mutation chance so severity 3 is reached quickly and often.
        Params eager = new Params(0.3, 0.5, 0.5, 0.9, 0.05, 0.8, 1.0);
        Simulation sim = new Simulation(SEED, eager,
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, PLANTED_IN)));
        sim.run(TICKS);
        WorldState state = sim.state();

        boolean reachedTheTop = false;
        for (Rumor rumor : state.rumors().values()) {
            reachedTheTop |= rumor.severity() == Rumor.MAX_SEVERITY;
            if (!rumor.isPlanted()) {
                assertTrue(state.rumor(rumor.parentId()).severity() < Rumor.MAX_SEVERITY,
                        "rumor " + rumor.id() + " grew out of one that was already as bad as it gets");
            }
        }
        assertTrue(reachedTheTop, "the run should have produced a severity 3 rumor");
    }

    @Test
    void beliefsFadeAndAreForgottenWhenNobodyRepeatsThem() {
        // One villager hears it, and by the end of day one still believes it.
        Simulation sim = new Simulation(SEED, Params.defaults(),
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, PLANTED_IN)));
        sim.run(4);
        assertTrue(believers(sim.state()) > 0);

        // A run with a much harsher decay should end up with fewer believers than the
        // default one, all else being equal.
        Params forgetful = new Params(0.3, 0.5, 0.5, 0.2, 0.05, 0.05, 1.0);
        Simulation forgetfulSim = new Simulation(SEED, forgetful,
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, PLANTED_IN)));
        forgetfulSim.run(TICKS);

        Simulation normal = runWithRumor();
        assertTrue(believers(forgetfulSim.state()) < believers(normal.state()),
                "harsher decay should leave fewer believers");
    }

    private static int believers(WorldState state) {
        int count = 0;
        for (Villager villager : state.villagers().values()) {
            if (!villager.beliefs().isEmpty()) {
                count++;
            }
        }
        return count;
    }
}
