package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Whether a village is calming down, winding up, or going round for ever. */
class SwingTest {

    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);
    private static final int BASE = 100;
    private static final int NOISE_FLOOR = 10; // a tenth of normal

    /** A run of prices, as a log the stats will read. */
    private static MarketStats pricesOf(int... prices) {
        List<Event> log = new ArrayList<>();
        for (int i = 0; i < prices.length; i++) {
            log.add(new MarketPriceSet(i + 1, prices[i], 10));
        }
        return MarketStats.of(log, DIAMONDS_SCARCE);
    }

    @Test
    void aPriceThatOnlyClimbsHasOneLeg() {
        List<Swing> legs = pricesOf(100, 110, 120, 130, 140).swings(NOISE_FLOOR);

        assertEquals(0, legs.size(), "nothing has turned yet, so no leg has finished");
    }

    @Test
    void aTurningPointEndsALeg() {
        List<Swing> legs = pricesOf(100, 140, 100).swings(NOISE_FLOOR);

        assertEquals(1, legs.size());
        assertEquals(40, legs.get(0).amplitude());
        assertTrue(legs.get(0).rising());
    }

    @Test
    void anOrdinaryWobbleIsNotAChangeOfMind() {
        // The market moves a few points every tick. If every one of those counted as a
        // turning point the measure would report hundreds of tiny legs and mean nothing.
        List<Swing> legs = pricesOf(100, 103, 99, 102, 98, 101, 100).swings(NOISE_FLOOR);

        assertTrue(legs.isEmpty(), "noise should not turn anything, but got " + legs);
    }

    @Test
    void aVillageCalmingDownShrinksItsSwings() {
        MarketStats calming = pricesOf(100, 160, 60, 140, 80, 120, 95);

        double decay = calming.swingDecay(NOISE_FLOOR).orElseThrow();
        assertTrue(decay < 1, "each swing should be smaller than the last, but decay was " + decay);
    }

    @Test
    void aVillageWindingUpGrowsThem() {
        MarketStats winding = pricesOf(100, 110, 95, 130, 70, 160, 40);

        double decay = winding.swingDecay(NOISE_FLOOR).orElseThrow();
        assertTrue(decay > 1, "each swing should be larger than the last, but decay was " + decay);
    }

    @Test
    void halvingThenDoublingHasGoneNowhere() {
        // Legs of 40, 20, 40: the swing halved and then doubled back, so the village is
        // exactly where it started. Averaging the ratios arithmetically would call that
        // 1.25 and report a village winding up when it is doing nothing of the kind.
        MarketStats backAndForth = pricesOf(100, 140, 120, 160, 120);

        List<Swing> legs = backAndForth.swings(NOISE_FLOOR);
        assertEquals(List.of(40, 20, 40), legs.stream().map(Swing::amplitude).toList(),
                "the fixture should make legs of 40, 20 and 40");
        assertEquals(1.0, backAndForth.swingDecay(NOISE_FLOOR).orElseThrow(), 1e-9,
                "halving and then doubling is no change at all");
    }

    @Test
    void aRunWithNothingToCompareReportsNothing() {
        assertTrue(pricesOf(100, 140, 100).swingDecay(NOISE_FLOOR).isEmpty(),
                "one leg cannot be compared to anything");
        assertTrue(pricesOf(100).swingDecay(NOISE_FLOOR).isEmpty());
    }

    @Test
    void settlingIsMeasuredFromTheEndBackwards() {
        // It has to answer "when did it settle and stay settled", not "when did it first
        // touch normal on the way past", or a village that crossed 100 on its way to 160
        // would be reported as having settled on day one.
        MarketStats settles = pricesOf(100, 160, 60, 140, 102, 101, 99, 100);

        assertEquals(5, settles.settledAt(BASE, 0.10).orElseThrow(),
                "it settled at the first of the run of quiet prices at the end");
    }

    @Test
    void aVillageStillSwingingWhenTheRunEndsNeverSettled() {
        MarketStats never = pricesOf(100, 160, 60, 140, 60, 150);

        assertTrue(never.settledAt(BASE, 0.10).isEmpty(),
                "reporting a number here would read as though it had calmed down");
    }
}
