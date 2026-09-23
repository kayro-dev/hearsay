package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Only the part of a move on the claim's side of normal is evidence for it (E47). */
class LevelGateTest {

    private static final int NORMAL = 100;
    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    private static double read(int from, int to, double gate) {
        double move = (to - from) / (double) from;
        return Simulation.beyondNormal(move, to, from, NORMAL, gate);
    }

    @Test
    void aGlutEndingIsNotAFamineStarting() {
        // E45's rebound: from the floor back toward normal, still below it.
        assertEquals(0.0, read(25, 48, 1.0));
        assertEquals(0.0, read(48, 99, 1.0));
    }

    @Test
    void aBubbleEndingIsNotAGlutStarting() {
        assertEquals(0.0, read(150, 120, 1.0));
        assertEquals(0.0, read(120, 101, 1.0));
    }

    @Test
    void crossingNormalCountsOnlyThePartBeyondIt() {
        assertEquals(0.10, read(90, 110, 1.0), 1e-12);
        assertEquals(-0.20, read(110, 80, 1.0), 1e-12);
    }

    @Test
    void movesAlreadyPastNormalReadAsTheyAlwaysDid() {
        // A lie's bubble growing, and a glut deepening: untouched, or lies would be muted.
        assertEquals((130 - 110) / 110.0, read(110, 130, 1.0), 1e-12);
        assertEquals((70 - 90) / 90.0, read(90, 70, 1.0), 1e-12);
    }

    @Test
    void offIsTodaysRuleAndHalfIsHalfway() {
        double today = (48 - 25) / 25.0;
        assertEquals(today, read(25, 48, 0.0), 0.0);
        assertEquals(today / 2, read(25, 48, 0.5), 1e-12);
    }

    @Test
    void onItChangesWhatTheVillageConcludes() {
        int planter = Run.execute(42, Params.defaults(), List.of(), 1)
                .finalState().gossipiestVillager().id();
        List<Input> lie = List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, planter));
        assertNotEquals(Run.execute(42, Params.defaults().withLevelGate(0.0), lie, 200).log(),
                Run.execute(42, Params.defaults().withLevelGate(1.0), lie, 200).log());
    }

    @Test
    void theGateIsTheDefault() {
        assertEquals(1.0, Params.defaults().levelGate(), "adopted in E47");
    }

    @Test
    void underTheDefaultsNoConclusionIsDrawnFromTheWrongSideOfNormal() {
        // The rule, read back off real runs: a lie, a player selling hard, a quiet village.
        // Every reading of scarcity was of a price above normal, every reading of plenty of
        // one below it. Today's rule broke this constantly — E45's rebounds are exactly a
        // shortage read off a price still at half of normal.
        int planter = Run.execute(3, Params.defaults(), List.of(), 1)
                .finalState().gossipiestVillager().id();
        List<Input> inputs = new java.util.ArrayList<>(
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 2, planter)));
        for (long tick = 80; tick < 200; tick += 4) {
            inputs.add(new PlayerTraded(tick, 0, Simulation.DIAMOND, 12, 96,
                    new java.util.TreeSet<>(java.util.Set.of(0, 1, 2, 3, 4))));
        }
        int scarce = 0;
        int plenty = 0;
        for (long seed : new long[] {3, 1001, 2160}) {
            for (List<Input> given : List.of(inputs, List.<Input>of())) {
                for (Event event : Run.execute(seed, Params.defaults(), given, 300).log()) {
                    if (event instanceof PriceObserved read) {
                        if (read.claim().type() == ClaimType.SCARCE) {
                            assertTrue(read.price() > NORMAL, "scarcity read off " + read);
                            scarce++;
                        } else {
                            assertTrue(read.price() < NORMAL, "plenty read off " + read);
                            plenty++;
                        }
                    }
                }
            }
        }
        assertTrue(scarce > 0 && plenty > 0, "both kinds of reading should have happened: "
                + scarce + " scarce, " + plenty + " plenty");
    }

    @Test
    void theSettingSurvivesARecipeAndAnOldRecipeReadsAsOff(@org.junit.jupiter.api.io.TempDir
                                                            java.nio.file.Path folder)
            throws Exception {
        java.nio.file.Path file = folder.resolve("level.hearsay");
        RecipeFile.write(Run.execute(42, Params.defaults().withLevelGate(0.75), List.of(), 20), file);
        assertEquals(0.75, RecipeFile.read(file).params().levelGate());

        String old = java.nio.file.Files.readString(file).replaceAll(" levelGate=\\S+", "");
        java.nio.file.Files.writeString(file, old);
        assertEquals(0.0, RecipeFile.read(file).params().levelGate());
    }
}
