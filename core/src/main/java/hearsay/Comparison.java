package hearsay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;

/**
 * Two versions of the same village, side by side: one where the lie was told and one where
 * it was not.
 *
 * <p>Reads two event logs and nothing else. Both timelines must come from the same seed and
 * the same params, differing only in the input that was removed; the streams being separate
 * is what makes that difference meaningful, because villagers walk the same routes and meet
 * the same people in both.
 */
public final class Comparison {

    /** One day in both timelines. */
    public record DayLine(int day, int priceWith, int priceWithout,
                          int heardWith, int heardWithout,
                          int believersWith, int believersWithout) {

        public int priceDifference() { return priceWith - priceWithout; }
    }

    /** What one timeline did, day by day. */
    private record Timeline(List<int[]> days, int peakPrice) {
        // each entry is {lastPrice, heard, believers}; lastPrice is 0 if the market never
        // opened that day
    }

    private final List<DayLine> daily;
    private final int peakPriceWith;
    private final int peakPriceWithout;
    private final Long divergenceTick;
    private final Claim claim;

    private Comparison(List<DayLine> daily, int peakPriceWith, int peakPriceWithout,
                       Long divergenceTick, Claim claim) {
        this.daily = daily;
        this.peakPriceWith = peakPriceWith;
        this.peakPriceWithout = peakPriceWithout;
        this.divergenceTick = divergenceTick;
        this.claim = claim;
    }

    public static Comparison of(List<Event> withLie, List<Event> withoutLie, Claim claim) {
        return of(withLie, withoutLie, claim, RumorStats.DEFAULT_BELIEVE_THRESHOLD);
    }

    public static Comparison of(List<Event> withLie, List<Event> withoutLie, Claim claim,
                                double believeThreshold) {
        Timeline with = walk(withLie, claim, believeThreshold);
        Timeline without = walk(withoutLie, claim, believeThreshold);

        List<DayLine> daily = new ArrayList<>();
        int days = Math.min(with.days().size(), without.days().size());
        for (int day = 0; day < days; day++) {
            int[] a = with.days().get(day);
            int[] b = without.days().get(day);
            daily.add(new DayLine(day + 1, a[0], b[0], a[1], b[1], a[2], b[2]));
        }

        return new Comparison(daily, with.peakPrice(), without.peakPrice(),
                firstDifference(withLie, withoutLie), claim);
    }

    private static Timeline walk(List<Event> log, Claim claim, double believeThreshold) {
        WorldState mirror = new WorldState();
        List<int[]> days = new ArrayList<>();
        int lastPrice = 0;
        int peak = 0;

        for (Event event : log) {
            mirror.apply(event);
            if (event instanceof MarketPriceSet priced) {
                lastPrice = priced.price();
                peak = Math.max(peak, priced.price());
            }
            if (event instanceof DayEnded) {
                int heard = 0;
                int believers = 0;
                for (Villager villager : mirror.villagers().values()) {
                    Belief held = villager.belief(claim);
                    if (held != null) {
                        heard++;
                        if (held.confidence() >= believeThreshold) {
                            believers++;
                        }
                    }
                }
                days.add(new int[] {lastPrice, heard, believers});
                lastPrice = 0; // a day with no market of its own reports no price
            }
        }
        return new Timeline(days, peak);
    }

    /**
     * The tick of the first event the two timelines disagree about. With paired randomness
     * this should be exactly the tick the lie was told; anything earlier means the two runs
     * differ for some reason other than the lie, and the comparison is worthless.
     */
    private static Long firstDifference(List<Event> a, List<Event> b) {
        int shared = Math.min(a.size(), b.size());
        for (int i = 0; i < shared; i++) {
            if (!a.get(i).equals(b.get(i))) {
                return Math.min(a.get(i).tick(), b.get(i).tick());
            }
        }
        if (a.size() == b.size()) {
            return null;
        }
        return a.size() > shared ? a.get(shared).tick() : b.get(shared).tick();
    }

    public OptionalLong divergenceTick() {
        return divergenceTick == null ? OptionalLong.empty() : OptionalLong.of(divergenceTick);
    }

    public boolean identical() { return divergenceTick == null; }

    public Claim claim() { return claim; }

    public List<DayLine> daily() { return Collections.unmodifiableList(daily); }

    public int peakPriceWith() { return peakPriceWith; }

    public int peakPriceWithout() { return peakPriceWithout; }

    public int daysAboveWith(int price) { return daysAbove(price, true); }

    public int daysAboveWithout(int price) { return daysAbove(price, false); }

    private int daysAbove(int price, boolean withLie) {
        int days = 0;
        for (DayLine line : daily) {
            if ((withLie ? line.priceWith() : line.priceWithout()) > price) {
                days++;
            }
        }
        return days;
    }

    /**
     * What the lie added to the bill for somebody buying one diamond a day.
     *
     * <p>Named for exactly what it measures, because nothing is actually traded yet: it is
     * the price difference summed over the days on which both villages held a market, not
     * a claim about anybody's losses.
     */
    public long extraCostOfADiamondEachMarketDay() {
        long total = 0;
        for (DayLine line : daily) {
            if (line.priceWith() > 0 && line.priceWithout() > 0) {
                total += line.priceDifference();
            }
        }
        return total;
    }

    public int peakBelieversWith() { return peakBelievers(true); }

    public int peakBelieversWithout() { return peakBelievers(false); }

    private int peakBelievers(boolean withLie) {
        int peak = 0;
        for (DayLine line : daily) {
            peak = Math.max(peak, withLie ? line.believersWith() : line.believersWithout());
        }
        return peak;
    }
}
