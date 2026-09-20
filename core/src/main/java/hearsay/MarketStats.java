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

    /** One day of the market, and of the claim being tracked. */
    public record DayOfTrading(int day, int highPrice, int lowPrice, int heard, int believers) {}

    private final Claim claim;
    private final double believeThreshold;
    private final List<DayOfTrading> daily;
    private final int peakPrice;
    private final Long halfBelievingAt;

    private MarketStats(Claim claim, double believeThreshold, List<DayOfTrading> daily,
                        int peakPrice, Long halfBelievingAt) {
        this.claim = claim;
        this.believeThreshold = believeThreshold;
        this.daily = daily;
        this.peakPrice = peakPrice;
        this.halfBelievingAt = halfBelievingAt;
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

        for (Event event : log) {
            mirror.apply(event);

            if (event instanceof MarketPriceSet priced) {
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
        return new MarketStats(claim, believeThreshold, daily, peakPrice, halfBelievingAt);
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
