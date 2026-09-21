package hearsay;

import java.util.Locale;

/**
 * What the price is doing, said the way a person would say it.
 *
 * <p>The index is built so that 100 is normal, which makes the raw number the less useful
 * half of the story: 138 means nothing until you have worked out that it is 38% dear. So
 * the change is the headline everywhere it is shown, and the index follows in smaller type.
 *
 * <p><strong>The colours are deliberately not a stock ticker's.</strong> Green for up and
 * red for down would say that a rising price is good news, and here it is the opposite: a
 * rising price is a village frightened of a shortage that may not exist. Rising runs warm,
 * from amber to red, the way an alarm does. Falling runs cool. Normal is plain white and
 * draws no attention, because most days nothing is happening.
 *
 * <p>The bands are the ones the rest of the model already uses, so a price the dashboard
 * paints as a panic is the same price {@link Bubble} counts as a bubble. Defining them
 * twice would let the picture and the measurement drift apart.
 *
 * <p>Those constants are written for a base price of 100, which is the default and what
 * every experiment was run at. They are taken as proportions here rather than as prices,
 * so a village priced in hundreds is not permanently panicking merely because its numbers
 * are large. {@link Bubble} itself still compares absolute prices, which is a limitation
 * of that class and not of this one.
 */
public enum PriceMood {

    /** Cheaper than anyone can explain: the village has talked itself into a glut. */
    GLUT("Glut", "#4a7fd4", '▼'),

    /** Sliding, but not yet strange. */
    EASING("Easing", "#6fa8dc", '▽'),

    /** Nothing worth remarking on. Most days are this. */
    NORMAL("Normal", "#b9b3a8", '·'),

    /** Dearer than usual, and somebody has noticed. */
    RISING("Rising", "#d99a2b", '△'),

    /** Dear enough to talk about. */
    ALARMED("Alarmed", "#d1622a", '▲'),

    /** A bubble, by the same threshold {@link Bubble#PEAK_ABOVE} counts one at. */
    PANIC("Panic", "#b02a1e", '▲');

    /** How far from normal, in percent, before it is worth calling anything. */
    private static final int QUIET = 5;

    private final String label;
    private final String hex;
    private final char arrow;

    PriceMood(String label, String hex, char arrow) {
        this.label = label;
        this.hex = hex;
        this.arrow = arrow;
    }

    /** The name a person would use. */
    public String label() {
        return label;
    }

    /**
     * The colour, as hex, so the page and the game agree without either owning the palette.
     * Minecraft takes hex directly and so does CSS.
     */
    public String hex() {
        return hex;
    }

    /** Which way it is going, at a glance. */
    public char arrow() {
        return arrow;
    }

    /** The base price the thresholds in {@link Bubble} are written against. */
    private static final double NOMINAL = 100.0;

    public static PriceMood of(int price, int basePrice) {
        double share = price / (double) basePrice * NOMINAL;
        if (share >= Bubble.PEAK_ABOVE) {
            return PANIC;
        }
        if (share >= Bubble.ELEVATED) {
            return ALARMED;
        }
        if (share > NOMINAL + QUIET) {
            return RISING;
        }
        if (share < Bubble.TROUGH_BELOW) {
            return GLUT;
        }
        if (share < NOMINAL - QUIET) {
            return EASING;
        }
        return NORMAL;
    }

    /**
     * How far from normal, as a percentage: what the headline says.
     *
     * @return for example {@code "▲ +38%"}, or {@code "· 0%"} when nothing is happening
     */
    public static String describe(int price, int basePrice) {
        long percent = Math.round((price - basePrice) * 100.0 / basePrice);
        PriceMood mood = of(price, basePrice);
        String sign = percent > 0 ? "+" : "";
        return String.format(Locale.ROOT, "%c %s%d%%", mood.arrow(), sign, percent);
    }
}
