package hearsay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;

/**
 * What the market did, and how many villagers held a claim regardless of where it came
 * from. Reads an event log and nothing else.
 *
 * <p>{@link RumorStats} counts believers in one rumor family, which is the right question
 * for "how far did that lie travel". This asks the other one: how many villagers believe
 * diamonds are scarce, however they came to think so. That distinction matters as soon as
 * the market can create a belief on its own, because a run with no rumor planted has no
 * family to count and would otherwise read as zero by construction rather than by
 * measurement.
 */
public final class MarketStats {

    /**
     * A bubble that burst: the price rose past one level and came back under another.
     *
     * @param peakTick     when the price was at its highest
     * @param recoveryTick the first tick after the peak back under the lower level
     * @param days         how long the fall took
     */
    public record Burst(int peakPrice, long peakTick, int recoveryPrice, long recoveryTick,
                        double days) {}

    /** One day of the market, and of the claim being tracked. */
    public record DayOfTrading(int day, int highPrice, int lowPrice, int heard, int believers) {}

    private final Claim claim;
    private final double believeThreshold;
    private final List<DayOfTrading> daily;
    private final int peakPrice;
    private final Long halfBelievingAt;
    private final List<int[]> priceSeries; // {tick, price}, in order

    private MarketStats(Claim claim, double believeThreshold, List<DayOfTrading> daily,
                        int peakPrice, Long halfBelievingAt, List<int[]> priceSeries) {
        this.claim = claim;
        this.believeThreshold = believeThreshold;
        this.daily = daily;
        this.peakPrice = peakPrice;
        this.halfBelievingAt = halfBelievingAt;
        this.priceSeries = priceSeries;
    }

    public static MarketStats of(List<Event> log, Claim claim) {
        return of(log, claim, RumorStats.DEFAULT_BELIEVE_THRESHOLD);
    }

    public static MarketStats of(List<Event> log, Claim claim, double believeThreshold) {
        WorldState mirror = new WorldState();
        List<DayOfTrading> daily = new ArrayList<>();

        int peakPrice = 0;
        int dayHigh = 0;
        int dayLow = Integer.MAX_VALUE;
        int day = 0;
        Long halfBelievingAt = null;
        List<int[]> priceSeries = new ArrayList<>();

        for (Event event : log) {
            mirror.apply(event);

            if (event instanceof MarketPriceSet priced) {
                priceSeries.add(new int[] {(int) priced.tick(), priced.price()});
                peakPrice = Math.max(peakPrice, priced.price());
                dayHigh = Math.max(dayHigh, priced.price());
                dayLow = Math.min(dayLow, priced.price());
            }
            if (halfBelievingAt == null
                    && believers(mirror, claim, believeThreshold) * 2 >= Simulation.VILLAGER_COUNT) {
                halfBelievingAt = event.tick();
            }
            if (event instanceof DayEnded) {
                day++;
                daily.add(new DayOfTrading(day, dayHigh, dayLow == Integer.MAX_VALUE ? 0 : dayLow,
                        holders(mirror, claim), believers(mirror, claim, believeThreshold)));
                dayHigh = 0;
                dayLow = Integer.MAX_VALUE;
            }
        }
        return new MarketStats(claim, believeThreshold, daily, peakPrice, halfBelievingAt,
                priceSeries);
    }

    private static int holders(WorldState state, Claim claim) {
        int count = 0;
        for (Villager villager : state.villagers().values()) {
            if (villager.belief(claim) != null) {
                count++;
            }
        }
        return count;
    }

    private static int believers(WorldState state, Claim claim, double threshold) {
        int count = 0;
        for (Villager villager : state.villagers().values()) {
            Belief held = villager.belief(claim);
            if (held != null && held.confidence() >= threshold) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether the price ran up past {@code peakAbove} and then came back under
     * {@code backBelow} before the run ended.
     *
     * <p>A bubble that never deflates is not a bubble but a change of regime, so this is
     * the measure that tells the two apart. Recovery is looked for after the highest price
     * the run reached: an earlier dip does not count as the run-up coming undone.
     */
    public java.util.Optional<Burst> burst(int peakAbove, int backBelow) {
        if (peakPrice <= peakAbove) {
            return java.util.Optional.empty();
        }
        long peakTick = 0;
        for (int[] point : priceSeries) {
            if (point[1] == peakPrice) {
                peakTick = point[0];
                break; // the first time it got that high
            }
        }
        for (int[] point : priceSeries) {
            if (point[0] > peakTick && point[1] < backBelow) {
                return java.util.Optional.of(new Burst(peakPrice, peakTick, point[1], point[0],
                        (point[0] - peakTick) / (double) TICKS_PER_DAY));
            }
        }
        return java.util.Optional.empty();
    }

    private static final int TICKS_PER_DAY = 4;

    public Claim claim() { return claim; }
    public double believeThreshold() { return believeThreshold; }

    public List<DayOfTrading> daily() { return Collections.unmodifiableList(daily); }

    /** The highest price the market ever settled on, or 0 if it never opened. */
    public int peakPrice() { return peakPrice; }

    /** Days on which the price went above the given level at least once. */
    public int daysAbove(int price) {
        int days = 0;
        for (DayOfTrading day : daily) {
            if (day.highPrice() > price) {
                days++;
            }
        }
        return days;
    }

    /**
     * The most villagers holding this claim at once at any strength. Catches a belief
     * taking hold well before anyone is convinced enough to count as a believer.
     */
    public int peakHolders() {
        int peak = 0;
        for (DayOfTrading day : daily) {
            peak = Math.max(peak, day.heard());
        }
        return peak;
    }

    /** The most villagers believing this claim at once, whatever led them to it. */
    public int peakBelievers() {
        int peak = 0;
        for (DayOfTrading day : daily) {
            peak = Math.max(peak, day.believers());
        }
        return peak;
    }

    /** When half the village first believed the claim, if it ever did. */
    public OptionalLong halfBelievingAt() {
        return halfBelievingAt == null ? OptionalLong.empty() : OptionalLong.of(halfBelievingAt);
    }

    public boolean reachedHalfBelieving() { return halfBelievingAt != null; }
}
