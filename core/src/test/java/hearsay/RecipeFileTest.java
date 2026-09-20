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
        assertEquals("hearsay-recipe 1", lines.get(0));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("seed 1234")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("meetingSource=EXTERNAL")));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("input plant 3 diamond SCARCE")));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("input meet ")));
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
