package hearsay;

/**
 * One leg of a price's wandering: from a turning point to the next one.
 *
 * <p>A bubble is one shape a price can make and a bust is the other, but E31 ran a village
 * that made five of one and four of the other over 177 days and never settled. Counting
 * bubbles said "five" and said nothing about whether the village was calming down or
 * winding up, which is the more interesting question and the one nothing measured.
 *
 * @param fromTick  the turning point this leg starts at
 * @param toTick    the turning point it ends at
 * @param fromPrice the price at the start
 * @param toPrice   the price at the end
 */
public record Swing(long fromTick, long toTick, int fromPrice, int toPrice) {

    /** How far the price moved, in price points, always positive. */
    public int amplitude() {
        return Math.abs(toPrice - fromPrice);
    }

    /** True if this leg went up. */
    public boolean rising() {
        return toPrice > fromPrice;
    }

    /** How long the leg took, in ticks. */
    public long ticks() {
        return toTick - fromTick;
    }
}
