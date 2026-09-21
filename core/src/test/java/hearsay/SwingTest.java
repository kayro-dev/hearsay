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

    /** A zigzag whose legs are each {@code ratio} times the one before. */
    private static MarketStats zigzag(int firstLeg, double ratio, int legs) {
        List<Integer> prices = new ArrayList<>();
        int at = 100;
        double leg = firstLeg;
        prices.add(at);
        for (int i = 0; i < legs; i++) {
            at += (i % 2 == 0 ? 1 : -1) * (int) Math.round(leg);
            prices.add(at);
            leg *= ratio;
        }
        int[] asArray = new int[prices.size()];
        for (int i = 0; i < prices.size(); i++) {
            asArray[i] = prices.get(i);
        }
        return pricesOf(asArray);
    }

    @Test
    void aVillageCalmingDownShrinksItsSwings() {
        // Long enough to be worth quoting: E36 found the figure meaningless below about ten
        // legs, because early swings are small while the rumour is still building.
        double decay = zigzag(60, 0.85, 14).swingDecay(NOISE_FLOOR).orElseThrow();

        assertTrue(decay < 1, "each swing should be smaller than the last, but decay was " + decay);
    }

    @Test
    void aVillageWindingUpGrowsThem() {
        double decay = zigzag(14, 1.15, 14).swingDecay(NOISE_FLOOR).orElseThrow();

        assertTrue(decay > 1, "each swing should be larger than the last, but decay was " + decay);
    }

    @Test
    void aRunTooShortToJudgeSaysSoRatherThanGuessing() {
        // Three legs is a ratio between a handful of noisy numbers, and quoting it is how
        // two comparisons came to be published and withdrawn in E36.
        assertTrue(zigzag(60, 0.85, 3).swingDecay(NOISE_FLOOR).isEmpty(),
                "a three-swing run should decline to answer");
        // Two extra legs, because a leg is only closed when the price turns back: the last
        // move in a zigzag is still in progress and does not count.
        assertTrue(zigzag(90, 0.92, MarketStats.ENOUGH_SWINGS + 2)
                        .swingDecay(NOISE_FLOOR).isPresent(),
                "a dozen legs is enough to answer");
    }

    @Test
    void halvingThenDoublingHasGoneNowhere() {
        // Legs alternating 40 and 20: the swing halves and doubles back, so the village is
        // exactly where it started. Averaging the ratios arithmetically would call that
        // 1.25 and report a village winding up when it is doing nothing of the kind.
        List<Integer> prices = new ArrayList<>();
        int at = 100;
        prices.add(at);
        for (int leg = 0; leg < 12; leg++) {
            at += (leg % 2 == 0 ? 1 : -1) * (leg % 2 == 0 ? 40 : 20);
            prices.add(at);
        }
        int[] asArray = new int[prices.size()];
        for (int i = 0; i < prices.size(); i++) {
            asArray[i] = prices.get(i);
        }
        MarketStats backAndForth = pricesOf(asArray);

        List<Swing> legs = backAndForth.swings(NOISE_FLOOR);
        assertTrue(legs.size() >= MarketStats.ENOUGH_SWINGS, "long enough to be quotable");
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
