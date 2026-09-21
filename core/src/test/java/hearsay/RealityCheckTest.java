package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The only rule in the model that pulls belief toward something instead of shoving it. */
class RealityCheckTest {

    private static final Claim SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    private static Run believedThenLooked(int stock, int looks) {
        List<Input> inputs = new ArrayList<>();
        inputs.add(new PlantRumor(1, SCARCE, 1, 0));
        for (int look = 0; look < looks; look++) {
            inputs.add(new RealityChecked(2 + look, 0, Simulation.DIAMOND, stock));
        }
        // No fading in the way: this is about what looking does, not what the night does.
        return Run.execute(42, Params.defaults().withDailyDecay(1.0), inputs, 2 + looks);
    }

    @Test
    void seeingPlentyMakesAVillagerLessSureOfAShortage() {
        double planted = believedThenLooked(64, 0).finalState()
                .villager(0).belief(SCARCE).confidence();
        double afterLooking = believedThenLooked(64, 1).finalState()
                .villager(0).belief(SCARCE).confidence();

        assertTrue(afterLooking < planted,
                "a villager certain of a shortage who sees a stack should waver: "
                        + planted + " to " + afterLooking);
    }

    /** What they still believe, or zero once it has faded past being a belief at all. */
    private static double stillBelieves(Run run) {
        Belief held = run.finalState().villager(0).belief(SCARCE);
        return held == null ? 0 : held.confidence();
    }

    @Test
    void lookingPullsTowardTheTruthRatherThanPastIt() {
        // The property that makes this damp anything: each look closes a fraction of the
        // gap, so belief converges instead of overshooting and swinging back.
        double one = stillBelieves(believedThenLooked(64, 1));
        double five = stillBelieves(believedThenLooked(64, 5));
        double twenty = stillBelieves(believedThenLooked(64, 20));

        assertTrue(five < one, "more looks should mean less certainty: " + one + " to " + five);
        assertTrue(twenty < five, "and more again: " + five + " to " + twenty);
        assertTrue(twenty >= 0, "never past what the stock implies");
    }

    @Test
    void enoughLookingAtAFullChestEndsTheBeliefAltogether() {
        // Drawn under forgetThreshold, the night takes it. A villager who has looked at a
        // stack of diamonds twenty times does not half-believe they are gone; they have
        // stopped believing it.
        assertNull(believedThenLooked(64, 20).finalState().villager(0).belief(SCARCE),
                "twenty looks at a stack should end it, not merely weaken it");
    }

    @Test
    void anEmptyVillageIsWeakerEvidenceThanAFullOne() {
        // Presence proves there are diamonds; absence proves only that none are in that
        // chest. Started from exactly half-sure, so both sights have the same distance to
        // close and only the weight can separate them — comparing unequal gaps would pass
        // on the gap alone, as an earlier version of this test did.
        Params params = Params.defaults().withDailyDecay(1.0).withPlantedConfidence(0.5);
        double movedByPlenty = Math.abs(0.5 - Run.execute(42, params, List.of(
                new PlantRumor(1, SCARCE, 1, 0),
                new RealityChecked(2, 0, Simulation.DIAMOND, 64)), 3)
                .finalState().villager(0).belief(SCARCE).confidence());
        double movedByNothing = Math.abs(0.5 - Run.execute(42, params, List.of(
                new PlantRumor(1, SCARCE, 1, 0),
                new RealityChecked(2, 0, Simulation.DIAMOND, 0)), 3)
                .finalState().villager(0).belief(SCARCE).confidence());

        assertTrue(movedByPlenty > movedByNothing,
                "a stack should move somebody further than an empty room, from the same "
                        + "starting point: " + movedByPlenty + " against " + movedByNothing);
    }

    @Test
    void lookingNeverInventsABeliefNobodyHeld() {
        // Otherwise a stocked market would be a way of starting a panic about plenty, and
        // a village nobody lied to could be talked into something by a chest.
        Run run = Run.execute(42, Params.defaults(), List.of(
                new RealityChecked(2, 0, Simulation.DIAMOND, 64),
                new RealityChecked(3, 0, Simulation.DIAMOND, 0)), 4);

        assertTrue(run.finalState().villager(0).beliefs().isEmpty(),
                "a villager with nothing to correct should not be corrected into an opinion");
    }

    @Test
    void whoToldThemIsNotRewrittenByWhatTheySaw() {
        Run told = Run.execute(42, Params.defaults().withDailyDecay(1.0), List.of(
                new PlantRumor(1, SCARCE, 1, 0),
                new RealityChecked(2, 0, Simulation.DIAMOND, 64)), 3);

        Belief after = told.finalState().villager(0).belief(SCARCE);
        assertEquals(Belief.NO_SOURCE, after.sourceId(),
                "looking at a chest changes how sure you are, not where you heard it");
    }

    @Test
    void aCheckReplaysAndSurvivesTheRecipe() throws Exception {
        Run run = believedThenLooked(64, 3);
        java.nio.file.Path file = java.nio.file.Files.createTempFile("hearsay", ".recipe");

        RecipeFile.write(run, file);

        assertEquals(run.finalState(), Simulation.replay(run.log()));
        assertEquals(run.inputs(), RecipeFile.read(file).inputs());
    }
}
