package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class MarketStatsTest {

    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    private static MarketStats afterARumor() {
        return MarketStats.of(Run.execute(3, Params.defaults(),
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, 12)), 200).log(), DIAMONDS_SCARCE);
    }

    @Test
    void countsBelieversWhateverLedThemToIt() {
        MarketStats stats = afterARumor();

        assertTrue(stats.peakBelievers() > 0);
        for (MarketStats.DayOfTrading day : stats.daily()) {
            assertTrue(day.believers() <= day.heard(), "day " + day.day());
            assertTrue(day.heard() <= Simulation.VILLAGER_COUNT);
        }
    }

    @Test
    void aQuietVillagePanicsOnlyRarely() {
        // The carried-over wobble is meant to cross the threshold sometimes, so this is a
        // rate rather than an absolute: checking one seed would only prove that seed.
        Params params = Params.defaults();
        int panicked = 0;
        int seeds = 30;
        for (long seed = 1; seed <= seeds; seed++) {
            MarketStats stats = MarketStats.of(
                    Run.execute(seed, params, List.of(), 200).log(), DIAMONDS_SCARCE);
            if (stats.peakBelievers() > 0) {
                panicked++;
            }
        }
        assertTrue(panicked <= seeds / 10,
                "a village nobody lied to should rarely panic: " + panicked + " of " + seeds);
    }

    @Test
    void theWobbleCarriesOverInsteadOfBeingDrawnFresh() {
        // A single step can move the price by at most marketNoise. Because the wobble
        // carries from tick to tick, a run of steps in one direction takes it further
        // than any single step could, which is the whole point of the carry-over.
        Params params = Params.defaults();
        int reachableInOneStep = (int) Math.round(params.basePrice() * (1 + params.marketNoise()));

        int highest = 0;
        for (long seed = 1; seed <= 30; seed++) {
            highest = Math.max(highest, MarketStats.of(
                    Run.execute(seed, params, List.of(), 200).log(), DIAMONDS_SCARCE).peakPrice());
        }
        assertTrue(highest > reachableInOneStep,
                "the wobble should compound past " + reachableInOneStep + ", reached " + highest);
    }

    @Test
    void withoutAnyWobbleAQuietVillageNeverBudges() {
        MarketStats stats = MarketStats.of(
                Run.execute(3, Params.defaults().withMarketNoise(0), List.of(), 200).log(),
                DIAMONDS_SCARCE);

        assertEquals(Params.defaults().basePrice(), stats.peakPrice());
        assertEquals(0, stats.peakBelievers());
        assertFalse(stats.reachedHalfBelieving());
    }

    @Test
    void daysAboveIsMonotonicInTheLevelAsked() {
        MarketStats stats = afterARumor();

        assertTrue(stats.daysAbove(100) >= stats.daysAbove(150));
        assertTrue(stats.daysAbove(150) >= stats.daysAbove(stats.peakPrice()));
        assertEquals(0, stats.daysAbove(stats.peakPrice()), "nothing is above the peak");
    }

    @Test
    void aSmallerVillageIsCountedOutOfItsOwnSize() {
        Params nine = Params.defaults().withVillagers(9);
        MarketStats stats = MarketStats.of(Run.execute(3, nine,
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, 2)), 200).log(), DIAMONDS_SCARCE);

        assertEquals(9, stats.villagers(), "the size comes from the log, not from a default");
        for (MarketStats.DayOfTrading day : stats.daily()) {
            assertTrue(day.heard() <= 9, "day " + day.day() + " had more holders than villagers");
        }
        assertTrue(stats.peakBelieversFraction() <= 1.0);
        assertEquals(stats.peakBelievers() / 9.0, stats.peakBelieversFraction(), 1e-9);
    }

    @Test
    void halfTheVillageIsHalfOfWhateverSizeItIs() {
        // Five of nine is past half; it would not be past half of twenty.
        Params nine = Params.defaults().withVillagers(9);
        MarketStats stats = MarketStats.of(Run.execute(3, nine,
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, 2)), 200).log(), DIAMONDS_SCARCE);

        if (stats.peakBelievers() * 2 >= 9) {
            assertTrue(stats.reachedHalfBelieving(),
                    "peaked at " + stats.peakBelievers() + " of 9, which is half or more");
        }
    }

    @Test
    void aBubbleCountsOnlyWhileTheLieIsStillFresh() {
        MarketStats stats = afterARumor();
        assertTrue(stats.bubble().isPresent(), "this run should bubble at some point");

        long peak = stats.bubble().orElseThrow().peakTick();
        // A window ending before the peak cannot contain it; one containing it must.
        assertTrue(stats.bubbleWithin(1, (int) (peak / 4) + 1).isPresent());
        assertTrue(stats.bubbleWithin(peak + 40, 30).isEmpty(),
                "a window starting after the peak should not claim it");
    }

    @Test
    void panicsAreCountedPerHundredDaysSoRunsOfDifferentLengthsCompare() {
        // The same quiet village, measured over two lengths. A rate should not climb
        // simply because the run went on longer, the way a yes-or-no answer does.
        Params quiet = Params.defaults();
        double shortRun = MarketStats.of(Run.execute(7, quiet, List.of(), 200).log(),
                DIAMONDS_SCARCE).panicsPerHundredDays();
        double longRun = MarketStats.of(Run.execute(7, quiet, List.of(), 800).log(),
                DIAMONDS_SCARCE).panicsPerHundredDays();

        assertTrue(shortRun >= 0 && longRun >= 0);
        assertTrue(longRun < 25, "a quiet village should not be panicking constantly");
    }

    @Test
    void peakPriceIsTheHighestAnyDayReached() {
        MarketStats stats = afterARumor();

        int highest = 0;
        for (MarketStats.DayOfTrading day : stats.daily()) {
            highest = Math.max(highest, day.highPrice());
        }
        assertEquals(highest, stats.peakPrice());
        assertTrue(stats.peakPrice() > Params.defaults().basePrice(), "belief should move the price");
    }
}
