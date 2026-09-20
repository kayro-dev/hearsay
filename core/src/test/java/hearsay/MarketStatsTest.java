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
    void aQuietVillageNeverLeavesTheNoiseBand() {
        // No rumor and no feedback: the price can only wobble by the market noise.
        MarketStats stats = MarketStats.of(
                Run.execute(3, Params.defaults(), List.of(), 200).log(), DIAMONDS_SCARCE);

        int base = Params.defaults().basePrice();
        int highestPossible = (int) Math.round(base * (1 + Params.defaults().marketNoise()));
        assertTrue(stats.peakPrice() <= highestPossible,
                "quiet village peaked at " + stats.peakPrice());
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
