package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The session report and the chronicle: what they may say, and that saying it changes nothing. */
class SessionReportTest {

    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    private static int planter(long seed, Params params) {
        return Run.execute(seed, params, List.of(), 1).finalState().gossipiestVillager().id();
    }

    /** A lie told to the gossip, and the player selling at the planter's own counter. */
    private static Run liedAndSold(long seed, int ticks) {
        Params params = Params.defaults();
        int planter = planter(seed, params);
        List<Input> inputs = new ArrayList<>(List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, planter)));
        for (long tick = 12; tick < 80; tick += 8) {
            inputs.add(new PlayerTraded(tick, planter, Simulation.DIAMOND, 3, 24,
                    new TreeSet<>(List.of(planter))));
        }
        return Run.execute(seed, params, inputs, ticks);
    }

    private static List<String> report(Run run) {
        return SessionReport.of(run, Optional.of(RecipeFile.checksum(run.log())));
    }

    @Test
    void readingASessionChangesNothing() {
        Run run = liedAndSold(1001, 200);
        WorldState before = Simulation.replay(run.log());
        List<Event> log = List.copyOf(run.log());

        report(run);
        Chronicle.of(run);

        assertEquals(log, run.log(), "reporting on a session altered its log");
        assertEquals(before, Simulation.replay(run.log()));
    }

    @Test
    void aRecipeThatReplaysIntoADifferentVillageIsRefused() {
        Run run = liedAndSold(1001, 60);
        assertThrows(SessionReport.NotThisSession.class,
                () -> SessionReport.of(run, Optional.of("not what was played")));
        assertTrue(report(run).get(1).startsWith("Replayed from its recipe and checked"));
        assertTrue(SessionReport.of(run, Optional.empty()).get(1).contains("cannot be checked"),
                "a recipe with no checksum should say it is unverified, not pretend otherwise");
    }

    @Test
    void aSavedSessionCarriesItsChecksum(@TempDir Path folder) {
        Run run = liedAndSold(1001, 60);
        Path file = folder.resolve("s.hearsay");
        RecipeFile.write(run, file);
        assertEquals(Optional.of(RecipeFile.checksum(run.log())), RecipeFile.checksumIn(file));
        assertEquals(RecipeFile.checksum(run.log()),
                RecipeFile.checksum(RecipeFile.read(file).log()), "the recipe reruns into the same log");
    }

    @Test
    void theEarningsSayWhatTheyAssume() {
        List<String> lines = report(liedAndSold(1001, 200));
        String all = String.join("\n", lines);
        assertTrue(all.contains("The same sales, at the same times, in a village nobody had lied to"));
        assertTrue(all.contains("This assumes you made exactly the same sales at exactly the same "
                + "times in the honest village."), all);
        assertFalse(all.contains("would have made"), "never what the player would have made");
    }

    @Test
    void theEarningsAreTheDifferenceOfTheTwoPricings() {
        // The planter believes the lie and prices their counter on it, so selling to them
        // during the panic takes more than an honest village would have paid.
        List<String> lines = report(liedAndSold(1001, 200));
        int took = figure(lines, "  You took (\\d+) emeralds.*");
        int repriced = lines.stream().anyMatch(l -> l.contains("sales come to"))
                ? figure(lines, ".*sales come to (\\d+);.*") : took;
        int honest = figure(lines, ".*would have paid (\\d+)\\.");
        int earned = figure(lines, "  Your lie earned you \\+(\\d+) emeralds?\\.");
        assertEquals(repriced - honest, earned, String.join("\n", lines));
        assertTrue(earned > 0, "a lie the trader believes should earn something:\n"
                + String.join("\n", lines));
    }

    @Test
    void itPricesASaleExactlyAsTheCounterDid() {
        // Traded the way the plugin trades: the counter is priced on the trader's ask after
        // each tick, the player pays that, and the sale reaches the village on the next tick.
        // The report must reprice every such sale to exactly what was paid.
        Params params = Params.defaults();
        int trader = planter(1001, params);
        Simulation village = new Simulation(1001, params,
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, trader)));
        for (int tick = 1; tick <= 120; tick++) {
            village.step();
            if (tick % 6 == 0) {
                int ask = (int) Math.round(village.askingPrice(village.state().villager(trader), Good.DIAMOND));
                village.schedule(new PlayerTraded(tick + 1, trader, Simulation.DIAMOND, 2,
                        2 * Good.DIAMOND.emeraldsAt(ask, params.basePrice()),
                        new TreeSet<>(List.of(trader))));
            }
        }
        List<String> lines = report(village.toRun());
        assertTrue(lines.stream().noneMatch(l -> l.contains("Repriced")),
                "the report priced the sales differently from the counter:\n" + String.join("\n", lines));
        assertTrue(lines.stream().anyMatch(l -> l.contains("earned you +") && !l.contains("+0")),
                "the trader believed the lie, so it should have earned something:\n"
                        + String.join("\n", lines));
    }

    private static int figure(List<String> lines, String pattern) {
        for (String line : lines) {
            if (line.matches(pattern)) {
                return Integer.parseInt(line.replaceAll(pattern, "$1"));
            }
        }
        throw new AssertionError("no line matching " + pattern + " in\n" + String.join("\n", lines));
    }

    @Test
    void aBubbleLongAfterTheLieIsNotLaidAtItsDoor() {
        // Under the reading before E47 villages swing repeatedly, so a bubble a month or more
        // after the lie is easy to find; the report must decline to blame the lie for it.
        Params params = Params.defaults().withLevelGate(0.0);
        for (long seed = 1001; seed < 1101; seed++) {
            Run run = Run.execute(seed, params,
                    List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, planter(seed, params))), 700);
            List<Bubble> late = MarketStats.of(run.log(), DIAMONDS_SCARCE).bubbles().stream()
                    .filter(b -> b.peakTick() > 1 + SessionReport.WITHIN_DAYS * 4L).toList();
            if (late.isEmpty()) {
                continue;
            }
            String all = String.join("\n", report(run));
            long day = DayPart.dayOf(late.get(0).peakTick());
            assertTrue(all.contains("The bubble on day " + day + " came more than 30 days after"), all);
            assertFalse(all.contains("The bubble on day " + day + " would not have happened"), all);
            return;
        }
        fail("no seed in 1001-1100 bubbled late; the fixture needs another seed range");
    }

    @Test
    void severalLiesAreWeighedOneAtATimeOnlyWhileThereAreFew() {
        Params params = Params.defaults();
        List<Input> two = List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, 0),
                new PlantRumor(5, DIAMONDS_SCARCE, 1, 1));
        assertTrue(String.join("\n", report(Run.execute(7, params, two, 80)))
                .contains("One lie at a time"));
        List<Input> four = new ArrayList<>(two);
        four.add(new PlantRumor(9, DIAMONDS_SCARCE, 1, 2));
        four.add(new PlantRumor(13, DIAMONDS_SCARCE, 1, 3));
        String all = String.join("\n", report(Run.execute(7, params, four, 80)));
        assertFalse(all.contains("One lie at a time"));
        assertTrue(all.contains("only weighed together"));
    }

    @Test
    void theChronicleNeverClaimsACauseAndPointsWhereTheReportDoes() {
        Run lied = liedAndSold(1001, 200);
        List<String> story = Chronicle.of(lied);
        String all = String.join("\n", story);
        for (String causal : List.of("because", "caused by", "would not have happened", "thanks to")) {
            assertFalse(all.contains(causal), "the chronicle claimed a cause: " + causal + "\n" + all);
        }
        assertTrue(all.contains(Chronicle.SEE_THE_REPORT), "the lied-about good's peak should point "
                + "at the report:\n" + all);
        assertTrue(story.stream().filter(l -> l.contains(Chronicle.SEE_THE_REPORT))
                .allMatch(l -> l.contains("Diamonds") || l.contains("diamonds")), all);

        // Nobody lied: nothing for the report to attribute, so nothing to point at.
        Run quiet = Run.execute(1165, Params.defaults().withLevelGate(0.0), List.of(), 700);
        assertFalse(String.join("\n", Chronicle.of(quiet)).contains(Chronicle.SEE_THE_REPORT));
    }

    @Test
    void theBriefVersionDropsOnlyWhatHappened() {
        Run run = liedAndSold(1001, 200);
        List<String> full = report(run);
        List<String> brief = SessionReport.brief(run, Optional.of(RecipeFile.checksum(run.log())));
        assertFalse(brief.contains("What happened"));
        assertTrue(brief.size() < full.size());
        for (String line : brief) {
            assertTrue(full.contains(line), "the brief says something the report does not: " + line);
        }
        for (String kept : List.of("What you did", "What your lie did", "What your lie earned you")) {
            assertTrue(brief.contains(kept), "the brief lost " + kept);
        }
        assertTrue(brief.stream().anyMatch(l -> l.contains("This assumes you made exactly the same sales")),
                "the earnings' assumption must survive into the brief");
    }

    @Test
    void aSessionFromBeforeTheGateSaysSoAtTheTopOfBoth() {
        Run before = Run.execute(7, Params.defaults().withLevelGate(0.0),
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, 0)), 60);
        Run after = Run.execute(7, Params.defaults(),
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, 0)), 60);
        assertTrue(report(before).contains(SessionReport.BEFORE_THE_GATE));
        assertEquals(SessionReport.BEFORE_THE_GATE, Chronicle.of(before).get(0));
        assertFalse(report(after).contains(SessionReport.BEFORE_THE_GATE));
        assertFalse(Chronicle.of(after).contains(SessionReport.BEFORE_THE_GATE));
    }

    @Test
    void theyNameGoodsTheWayAPersonWould() {
        Params params = Params.defaults().withGoods(Good.values());
        Claim bread = new Claim(Good.BREAD.id(), ClaimType.SCARCE);
        Run run = Run.execute(3, params, List.of(new PlantRumor(1, bread, 1, 4)), 60);
        assertTrue(String.join("\n", report(run)).contains("that bread is getting scarce"));
        assertTrue(String.join("\n", Chronicle.of(run)).contains("that bread is getting scarce"));
    }
}
