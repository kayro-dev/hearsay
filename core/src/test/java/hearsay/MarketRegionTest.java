package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** A marked market: who is standing in it, decided by where they are and nothing else. */
class MarketRegionTest {

    private static final MarketRegion SQUARE = new MarketRegion(100, 64, -40, 10);

    @Test
    void somebodyStandingInItIsInIt() {
        assertTrue(SQUARE.contains(100, 64, -40), "the centre is in the market");
        assertTrue(SQUARE.contains(109, 64, -40), "nine blocks out, still in it");
        assertTrue(SQUARE.contains(100, 64, -30), "exactly on the edge counts");
    }

    @Test
    void somebodyStandingOutsideItIsNot() {
        assertFalse(SQUARE.contains(111, 64, -40));
        assertFalse(SQUARE.contains(100, 64, -29.9));
    }

    @Test
    void heightCounts() {
        // A villager in a cellar under the square is not at the market, and a flat circle
        // would say they were. Ten blocks down is as far away as ten blocks sideways.
        assertFalse(SQUARE.contains(100, 75, -40), "eleven blocks above the square");
        assertTrue(SQUARE.contains(100, 70, -40), "six blocks up is still the square");
    }

    @Test
    void aMarketTooSmallOrTooLargeToMeanAnythingIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new MarketRegion(0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new MarketRegion(0, 0, 0, 100));
        assertThrows(IllegalArgumentException.class, () -> new MarketRegion(0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MarketRegion(0, 0, 0, -5));
    }

    @Test
    void itSaysHowBigItIsInTermsAPlayerPaces() {
        assertEquals(20, SQUARE.diameter());
    }
}
