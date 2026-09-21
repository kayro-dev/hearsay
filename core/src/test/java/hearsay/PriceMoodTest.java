package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The price, said the way a person would say it. */
class PriceMoodTest {

    private static final int BASE = 100;

    @Test
    void theHeadlineIsTheChangeNotTheIndex() {
        assertEquals("▲ +38%", PriceMood.describe(138, BASE));
        assertEquals("▼ -27%", PriceMood.describe(73, BASE));
        assertEquals("· 0%", PriceMood.describe(100, BASE));
    }

    @Test
    void theChangeIsRelativeToWhateverNormalIs() {
        // basePrice is a setting, and a village priced in hundreds is not permanently
        // panicking just because its numbers are large.
        assertEquals("· 0%", PriceMood.describe(500, 500));
        // 20% dear is the same band at any base: alarmed, not merely rising.
        assertEquals("▲ +20%", PriceMood.describe(600, 500));
        assertEquals(PriceMood.of(120, 100), PriceMood.of(600, 500));
        assertEquals(PriceMood.of(130, 100), PriceMood.of(650, 500));
        assertEquals(PriceMood.of(110, 100), PriceMood.of(550, 500));
    }

    @Test
    void aQuietMarketIsNotWorthColouring() {
        assertEquals(PriceMood.NORMAL, PriceMood.of(100, BASE));
        assertEquals(PriceMood.NORMAL, PriceMood.of(105, BASE));
        assertEquals(PriceMood.NORMAL, PriceMood.of(95, BASE));
        assertEquals(PriceMood.RISING, PriceMood.of(106, BASE));
        assertEquals(PriceMood.EASING, PriceMood.of(94, BASE));
    }

    @Test
    void thePanicBandIsTheSameOneABubbleIsCountedAt() {
        // If these came apart, the page would paint a panic the statistics did not count,
        // or count a bubble the page drew as ordinary.
        assertEquals(PriceMood.PANIC, PriceMood.of(Bubble.PEAK_ABOVE, BASE));
        assertNotEquals(PriceMood.PANIC, PriceMood.of(Bubble.PEAK_ABOVE - 1, BASE));
        assertEquals(PriceMood.GLUT, PriceMood.of(Bubble.TROUGH_BELOW - 1, BASE));
        assertEquals(PriceMood.ALARMED, PriceMood.of(Bubble.ELEVATED, BASE));
    }

    @Test
    void risingRunsWarmAndFallingRunsCool() {
        // Never a stock ticker's green and red: here a rising price is the village
        // frightened of a shortage that may not be there, and colouring that as good news
        // would tell the player the opposite of what is happening.
        assertTrue(redOf(PriceMood.PANIC) > blueOf(PriceMood.PANIC), "panic should run warm");
        assertTrue(redOf(PriceMood.ALARMED) > blueOf(PriceMood.ALARMED), "alarm should run warm");
        assertTrue(redOf(PriceMood.RISING) > blueOf(PriceMood.RISING), "rising should run warm");
        assertTrue(blueOf(PriceMood.GLUT) > redOf(PriceMood.GLUT), "a glut should run cool");
        assertTrue(blueOf(PriceMood.EASING) > redOf(PriceMood.EASING), "easing should run cool");
    }

    @Test
    void everyMoodHasAColourBothTheGameAndTheWebCanRead() {
        for (PriceMood mood : PriceMood.values()) {
            assertTrue(mood.hex().matches("#[0-9a-f]{6}"),
                    mood + " has a colour neither Minecraft nor CSS will take: " + mood.hex());
            assertFalse(mood.label().isBlank(), mood + " has no name");
        }
    }

    private static int redOf(PriceMood mood) {
        return Integer.parseInt(mood.hex().substring(1, 3), 16);
    }

    private static int blueOf(PriceMood mood) {
        return Integer.parseInt(mood.hex().substring(5, 7), 16);
    }
}
