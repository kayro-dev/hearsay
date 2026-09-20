package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A run carries its own recipe, and the recipe alone is enough to produce it again. */
class RunTest {

    private static final int TICKS = 200; // 50 days
    private static final long SEED = 42;
    private static final Claim DIAMONDS_SCARCE = new Claim("diamond", ClaimType.SCARCE);

    private static List<Input> twoRumors() {
        return List.of(
                new PlantRumor(1, DIAMONDS_SCARCE, 1, 18),
                new PlantRumor(60, DIAMONDS_SCARCE.opposite(), 2, 10));
    }

    private static Run aRun() {
        return Run.execute(SEED, Params.defaults(), twoRumors(), TICKS);
    }

    @Test
    void aRunKeepsTheRecipeThatProducedIt() {
        Run run = aRun();

        assertEquals(SEED, run.seed());
        assertEquals(Params.defaults(), run.params());
        assertEquals(twoRumors(), run.inputs());
        assertEquals(TICKS, run.ticks());
        assertFalse(run.log().isEmpty());
    }

    @Test
    void theRecipeAloneRebuildsTheIdenticalLog() {
        Run run = aRun();

        // Stronger than replay: nothing is re-applied, everything is decided again.
        assertEquals(run.log(), run.rerun().log());
        assertEquals(run.finalState(), run.rerun().finalState());
    }

    @Test
    void everyPlantedRumorAppearsInTheLogAsAnInputEvent() {
        Run run = aRun();

        List<Event> inputEvents = run.inputEvents();
        assertEquals(run.inputs().size(), inputEvents.size());
        for (Event event : inputEvents) {
            assertTrue(event.isInput());
        }
        for (Event event : run.log()) {
            assertEquals(event instanceof RumorPlanted, event.isInput(),
                    "only planted rumors are input events so far: " + event);
        }
    }

    @Test
    void droppingAnInputLeavesTheVillageWalkingTheSameRoutes() {
        Run run = aRun();
        Run counterfactual = run.without(twoRumors().get(0));

        assertEquals(twoRumors().size() - 1, counterfactual.inputs().size());
        assertEquals(physical(run.log()), physical(counterfactual.log()),
                "removing an input must not move anybody");
        assertNotEquals(run.log(), counterfactual.log(), "but it must change what was said");
    }

    private static List<Event> physical(List<Event> log) {
        List<Event> found = new ArrayList<>();
        for (Event event : log) {
            if (event instanceof VillagerMoved || event instanceof VillagersMet) {
                found.add(event);
            }
        }
        return found;
    }

    @Test
    void replayNeedsNothingButTheEventsHoweverTheKnobsAreRetunedToday() {
        Run run = aRun();

        // Replay consults no params at all: every event carries the numbers its own
        // consequences depend on.
        assertEquals(run.finalState(), Simulation.replay(run.log()));

        // ...and the run really was sensitive to those knobs, so the line above is not
        // passing because the params never mattered.
        Params retuned = Params.defaults().withDailyDecay(0.2).withForgetThreshold(0.4);
        Run underNewKnobs = Run.execute(run.seed(), retuned, run.inputs(), run.ticks());
        assertNotEquals(run.log(), underNewKnobs.log());
    }
}
