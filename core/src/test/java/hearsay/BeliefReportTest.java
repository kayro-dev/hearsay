package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Asking a villager what they have heard, without changing what they think. */
class BeliefReportTest {

    private static final Claim SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    private static Run villageToldALie() {
        Params params = Params.defaults().withMixing(1.0);
        int planter = Run.execute(42, params, List.of(), 1).finalState().gossipiestVillager().id();
        return Run.execute(42, params, List.of(new PlantRumor(1, SCARCE, 1, planter)), 120);
    }

    @Test
    void askingChangesNothing() {
        // The whole reason this could be added without re-running a single experiment. If
        // reading a villager's mind could alter it, every sweep in EXPERIMENTS.md would be
        // measuring a village that had been interrogated.
        Run run = villageToldALie();
        WorldState world = run.finalState();
        WorldState untouched = Simulation.replay(run.log());

        for (int id = 0; id < run.params().villagers(); id++) {
            BeliefReport.of(world, id);
        }

        assertEquals(untouched, world, "asking the village what it thinks changed what it thinks");
    }

    @Test
    void aVillagerWhoHasHeardNothingSaysNothing() {
        WorldState fresh = Simulation.replay(
                Run.execute(42, Params.defaults(), List.of(), 8).log());

        assertTrue(BeliefReport.of(fresh, 0).isEmpty(),
                "a villager with no beliefs should have nothing to report, not a blank line");
    }

    @Test
    void itSaysWhatTheyThinkAndHowSure() {
        WorldState world = villageToldALie().finalState();

        String said = findSomebodyWhoBelieves(world);
        assertTrue(said.contains(Simulation.DIAMOND), "it should name the thing: " + said);
        assertTrue(said.contains("certain") || said.contains("sure")
                        || said.contains("half believes") || said.contains("barely"),
                "it should say how sure they are, in words: " + said);
    }

    @Test
    void villagersSpeakEnglish() {
        // Claims name the item in the singular, so anything reading one back has to
        // pluralise it. "diamond are running short" is nobody talking.
        WorldState world = villageToldALie().finalState();

        String said = findSomebodyWhoBelieves(world);
        assertTrue(said.contains("diamonds"), "should say diamonds, not diamond: " + said);
    }

    @Test
    void itSaysWhereTheyHadItFrom() {
        WorldState world = villageToldALie().finalState();

        String said = findSomebodyWhoBelieves(world);
        assertTrue(said.contains("heard it from") || said.contains("told them so directly")
                        || said.contains("at the market") || said.contains("witnessed a sale"),
                "every belief came from somewhere and should say so: " + said);
    }

    @Test
    void theVillagerItWasPlantedInKnowsItCameFromNobody() {
        Params params = Params.defaults().withMixing(1.0);
        WorldState world = Simulation.replay(
                Run.execute(42, params, List.of(new PlantRumor(1, SCARCE, 1, 3)), 2).log());

        assertTrue(BeliefReport.of(world, 3).get(0).contains("told them so directly"),
                "a planted rumour has no villager behind it: " + BeliefReport.of(world, 3));
    }

    private static String findSomebodyWhoBelieves(WorldState world) {
        for (Villager villager : world.villagers().values()) {
            List<String> said = BeliefReport.of(world, villager.id());
            if (!said.isEmpty()) {
                return said.get(0);
            }
        }
        return fail("nobody in this village believes anything, so there is nothing to report");
    }
}
