package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The page has to stand on its own, and say what the two runs actually showed. */
class DashboardPageTest {

    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);
    private static final int TICKS = 200;

    private static String pageFor(long seed) {
        Params params = Params.defaults();
        int planter = Run.execute(seed, params, List.of(), 1).finalState().gossipiestVillager().id();
        List<Input> lie = List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, planter));

        Run withLie = Run.execute(seed, params, lie, TICKS);
        Run withoutLie = Run.execute(seed, params, List.of(), TICKS);
        return DashboardPage.render(seed, TICKS, params, DIAMONDS_SCARCE,
                MarketStats.of(withLie.log(), DIAMONDS_SCARCE),
                MarketStats.of(withoutLie.log(), DIAMONDS_SCARCE));
    }

    @Test
    void thePageNeedsNothingFromAnywhereElse() {
        // It has to open from a file:// URL, where a browser refuses to fetch anything at
        // all. A page that reached for a stylesheet or a chart library would be blank.
        String page = pageFor(1001);

        assertFalse(page.contains("http://"), "the page reaches out to the network");
        assertFalse(page.contains("https://"), "the page reaches out to the network");
        assertFalse(page.contains("<script"), "the page needs scripting to draw itself");
        assertTrue(page.contains("<svg"), "the charts should be drawn into the page");
    }

    @Test
    void thePageReportsBothTimelines() {
        long seed = 1001;
        Params params = Params.defaults();
        int planter = Run.execute(seed, params, List.of(), 1).finalState().gossipiestVillager().id();
        MarketStats withLie = MarketStats.of(Run.execute(seed, params,
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, planter)), TICKS).log(), DIAMONDS_SCARCE);
        MarketStats withoutLie = MarketStats.of(
                Run.execute(seed, params, List.of(), TICKS).log(), DIAMONDS_SCARCE);

        String page = DashboardPage.render(seed, TICKS, params, DIAMONDS_SCARCE, withLie, withoutLie);

        assertTrue(page.contains(String.valueOf(withLie.peakPrice())),
                "the peak with the lie is missing");
        assertTrue(page.contains(String.valueOf(withoutLie.peakPrice())),
                "the peak without it is missing, so there is nothing to compare");
        assertTrue(page.contains("path class=\"quiet\""),
                "the counterfactual is not drawn, which is the whole point of the page");
        assertTrue(page.contains(String.valueOf(seed)), "the seed is not on the page");
    }

    @Test
    void theVerdictMatchesWhatTheRunsDid() {
        // A page that claimed a cause the numbers do not support would be worse than none.
        // Seeds 1165 and 2160 are the awkward ones: those villages bubble with nobody
        // lying to them, so "it bubbled" and "the lie caused it" come apart, and a page
        // that cannot tell them apart says something false about exactly those runs.
        for (long seed : new long[] {1001, 1002, 1003, 1165, 2160}) {
            Params params = Params.defaults();
            int planter = Run.execute(seed, params, List.of(), 1)
                    .finalState().gossipiestVillager().id();
            MarketStats withLie = MarketStats.of(Run.execute(seed, params,
                    List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, planter)), TICKS).log(),
                    DIAMONDS_SCARCE);
            MarketStats withoutLie = MarketStats.of(
                    Run.execute(seed, params, List.of(), TICKS).log(), DIAMONDS_SCARCE);
            String page = DashboardPage.render(seed, TICKS, params, DIAMONDS_SCARCE,
                    withLie, withoutLie);

            boolean claimsCause = page.contains("The lie caused a bubble");
            boolean reallyCaused = !withLie.bubbles().isEmpty() && withoutLie.bubbles().isEmpty();
            assertEquals(reallyCaused, claimsCause,
                    "seed " + seed + " claims a cause the runs do not show");
        }
    }

    @Test
    void aVillageWhereNothingHappensStillDraws() {
        Params params = Params.defaults();
        MarketStats quiet = MarketStats.of(
                Run.execute(7, params, List.of(), 8).log(), DIAMONDS_SCARCE);

        String page = assertDoesNotThrow(
                () -> DashboardPage.render(7, 8, params, DIAMONDS_SCARCE, quiet, quiet));
        assertTrue(page.contains("</html>"), "the page should be complete even when empty");
    }
}
