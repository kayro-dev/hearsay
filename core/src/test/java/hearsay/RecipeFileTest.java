package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The bridge out of the game: a session saved, then asked questions of. */
class RecipeFileTest {

    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    /** A run of the shape the plugin saves: meetings observed, not simulated. */
    private static Run anInGameSession() {
        Params params = Params.defaults().withMeetingSource(MeetingSource.EXTERNAL);
        List<Input> inputs = new ArrayList<>();
        inputs.add(new PlantRumor(3, DIAMONDS_SCARCE, 1, 4));
        for (long tick = 2; tick <= 30; tick++) {
            inputs.add(new ObservedMeeting(tick, (int) (tick % 6), (int) (tick % 6) + 8, Spot.MARKET));
        }
        return Run.execute(1234, params, inputs, 30);
    }

    @Test
    void aSavedSessionRerunsToTheIdenticalLog(@TempDir Path folder) {
        Run played = anInGameSession();
        Path file = folder.resolve("session.hearsay");

        RecipeFile.write(played, file);
        Run loaded = RecipeFile.read(file);

        assertEquals(played.seed(), loaded.seed());
        assertEquals(played.params(), loaded.params());
        assertEquals(played.inputs(), loaded.inputs());
        assertEquals(played.ticks(), loaded.ticks());
        assertEquals(played.log(), loaded.log(), "the saved session replayed differently");
    }

    @Test
    void aSavedSessionCanBeAskedWhatWouldHaveHappenedWithoutTheLie(@TempDir Path folder) {
        Run played = anInGameSession();
        Path file = folder.resolve("session.hearsay");
        RecipeFile.write(played, file);

        Run loaded = RecipeFile.read(file);
        Run withoutTheLie = loaded.without(new PlantRumor(3, DIAMONDS_SCARCE, 1, 4));

        Comparison comparison = Comparison.of(loaded.log(), withoutTheLie.log(), DIAMONDS_SCARCE);
        assertEquals(3, comparison.divergenceTick().orElseThrow(),
                "the timelines should part company where the lie was told");
        // The meetings were observed, not decided, so they happen in both timelines alike.
        assertEquals(meetings(withoutTheLie.log()), meetings(loaded.log()));
    }

    private static List<Event> meetings(List<Event> log) {
        List<Event> found = new ArrayList<>();
        for (Event event : log) {
            if (event instanceof VillagersMet) {
                found.add(event);
            }
        }
        return found;
    }

    @Test
    void theFileIsPlainEnoughToReadWithoutTooling(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("session.hearsay");
        RecipeFile.write(anInGameSession(), file);

        List<String> lines = Files.readAllLines(file);
        assertEquals("hearsay-recipe 2", lines.get(0));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("seed 1234")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("meetingSource=EXTERNAL")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("villagers=")));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("input plant 3 diamond SCARCE")));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("input meet ")));
    }

    @Test
    void aRecipeWrittenBeforeVillageSizeExistedStillLoads(@TempDir Path folder) throws Exception {
        // Exactly what the plugin used to write: version 1, with no villagers field.
        Path old = folder.resolve("old.hearsay");
        Files.writeString(old, """
                hearsay-recipe 1
                seed 99
                ticks 20
                params tellThreshold=0.4 repeatWeight=0.25 contradictionFactor=0.5 \
                dailyDecay=0.92 forgetThreshold=0.05 mutationChance=0.05 plantedConfidence=1.0 \
                basePrice=100 priceSensitivity=0.75 observationWeight=0.25 \
                observationThreshold=0.1 fullMoveSize=0.2 marketNoise=0.03 noiseDecay=0.86 \
                marketQuorum=5 meetingSource=SIMULATED
                input plant 3 diamond SCARCE 1 4
                """.replace("\\\n                ", " "));

        Run loaded = RecipeFile.read(old);

        assertEquals(Simulation.VILLAGER_COUNT, loaded.params().villagers(),
                "a recipe from before the field existed was written when every village had 20");
        assertEquals(99, loaded.seed());
        assertEquals(Simulation.VILLAGER_COUNT, loaded.finalState().villagers().size());
    }

    @Test
    void somethingThatIsNotARecipeIsRefused(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("nonsense.txt");
        Files.writeString(file, "this is not a recipe\n");

        assertThrows(IllegalArgumentException.class, () -> RecipeFile.read(file));
    }

    @Test
    void aHeadlessRunSurvivesTheRoundTripToo(@TempDir Path folder) {
        Run headless = Run.execute(42, Params.defaults(),
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, 10)), 80);
        Path file = folder.resolve("headless.hearsay");

        RecipeFile.write(headless, file);

        assertEquals(headless.log(), RecipeFile.read(file).log());
    }
}
