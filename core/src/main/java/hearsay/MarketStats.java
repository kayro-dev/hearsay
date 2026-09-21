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
    private final int villagers;
    private final List<int[]> priceSeries; // {tick, price}, in order
    private final int tellings;
    private final int priceReadings;

    private MarketStats(Claim claim, double believeThreshold, List<DayOfTrading> daily,
                        int peakPrice, Long halfBelievingAt, List<int[]> priceSeries,
                        int villagers, int tellings, int priceReadings) {
        this.claim = claim;
        this.believeThreshold = believeThreshold;
        this.daily = daily;
        this.peakPrice = peakPrice;
        this.halfBelievingAt = halfBelievingAt;
        this.priceSeries = priceSeries;
        this.villagers = villagers;
        this.tellings = tellings;
        this.priceReadings = priceReadings;
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
        int tellings = 0;
        int priceReadings = 0;

        for (Event event : log) {
            mirror.apply(event);

            if (event instanceof RumorTold told && mirror.rumor(told.keptRumorId()).claim().equals(claim)) {
                tellings++;
            }
            if (event instanceof PriceObserved read && read.claim().equals(claim)) {
                priceReadings++;
            }
            if (event instanceof MarketPriceSet priced) {
                priceSeries.add(new int[] {(int) priced.tick(), priced.price()});
                peakPrice = Math.max(peakPrice, priced.price());
                dayHigh = Math.max(dayHigh, priced.price());
                dayLow = Math.min(dayLow, priced.price());
            }
            if (halfBelievingAt == null && mirror.villagers().size() > 0
                    && believers(mirror, claim, believeThreshold) * 2 >= mirror.villagers().size()) {
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
                priceSeries, mirror.villagers().size(), tellings, priceReadings);
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

    /** Whether this run bubbled, by the one definition in {@link Bubble}. */
    public java.util.Optional<Bubble> bubble() {
        return burst(Bubble.PEAK_ABOVE, Bubble.BACK_BELOW);
    }

    /** Days the price spent above {@link Bubble#ELEVATED}, which is not the same question. */
    public int daysElevated() {
        return daysAbove(Bubble.ELEVATED);
    }

    /**
     * Whether the price ran up past {@code peakAbove} and then came back under
     * {@code backBelow} before the run ended. Prefer {@link #bubble()} unless a run
     * deliberately asks a different question of the same shape.
     *
     * <p>Recovery is looked for after the highest price the run reached: an earlier dip
     * does not count as the run-up coming undone.
     */
    public java.util.Optional<Bubble> burst(int peakAbove, int backBelow) {
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
                return java.util.Optional.of(new Bubble(peakPrice, peakTick, point[1], point[0],
                        (point[0] - peakTick) / (double) TICKS_PER_DAY));
            }
        }
        return java.util.Optional.empty();
    }

    private static final int TICKS_PER_DAY = 4;

    /** How many villagers the log says there were. Read from the log, never assumed. */
    public int villagers() { return villagers; }

    /** The most villagers believing at once, as a share of the village. */
    public double peakBelieversFraction() {
        return villagers == 0 ? 0 : peakBelievers() / (double) villagers;
    }

    /** The most villagers holding the claim at once, as a share of the village. */
    public double peakHoldersFraction() {
        return villagers == 0 ? 0 : peakHolders() / (double) villagers;
    }

    /**
     * Every bubble the run went through, not just the biggest.
     *
     * <p>{@link #bubble()} answers whether the run's highest price came back down, which is
     * one question about one moment. This finds each separate excursion past
     * {@link Bubble#PEAK_ABOVE} that later came back under {@link Bubble#BACK_BELOW}, so a
     * long run that bubbled three times says so.
     */
    public List<Bubble> bubbles() {
        List<Bubble> found = new ArrayList<>();
        boolean climbing = false;
        int peak = 0;
        long peakAt = 0;

        for (int[] point : priceSeries) {
            long tick = point[0];
            int price = point[1];
            if (price > Bubble.PEAK_ABOVE) {
                if (!climbing || price > peak) {
                    peak = price;
                    peakAt = tick;
                }
                climbing = true;
            } else if (climbing && price < Bubble.BACK_BELOW) {
                found.add(new Bubble(peak, peakAt, price, tick,
                        (tick - peakAt) / (double) TICKS_PER_DAY));
                climbing = false;
            }
        }
        return found;
    }

    /** How many times somebody was told this claim by another villager. */
    public int tellings() { return tellings; }

    /** How many times somebody read this claim out of the market price instead. */
    public int priceReadings() { return priceReadings; }

    /**
     * What share of the evidence in this run came from one villager telling another, as
     * against the price telling everyone at once. Empty when neither ever happened.
     *
     * <p>Worth its own measure because a run can reach the same peak either way, and the
     * two are not the same story. This project is about a rumor spreading; a village that
     * arrived at the same price by watching the market has done something else, and no
     * sweep before E29 would have noticed the difference. E28 ran at 0.14 here while E26
     * ran at 0.50, on peaks that looked comparable.
     */
    public java.util.OptionalDouble shareFromGossip() {
        int both = tellings + priceReadings;
        return both == 0 ? java.util.OptionalDouble.empty()
                : java.util.OptionalDouble.of(tellings / (double) both);
    }

    /**
     * Every bust: each excursion under {@link Bubble#TROUGH_BELOW} that later came back
     * over {@link Bubble#BACK_ABOVE}.
     *
     * <p>The mirror of {@link #bubbles()}, and it exists because the loop runs both ways.
     * A price that has been talked up gets talked down again past where it started, since
     * a falling price is evidence of plenty on exactly the terms a rising one was evidence
     * of scarcity. Reported as a {@link Bubble} with its trough where a bubble has its
     * peak, so the two read the same way round.
     */
    public List<Bubble> busts() {
        List<Bubble> found = new ArrayList<>();
        boolean falling = false;
        int trough = Integer.MAX_VALUE;
        long troughAt = 0;

        for (int[] point : priceSeries) {
            long tick = point[0];
            int price = point[1];
            if (price < Bubble.TROUGH_BELOW) {
                if (!falling || price < trough) {
                    trough = price;
                    troughAt = tick;
                }
                falling = true;
            } else if (falling && price > Bubble.BACK_ABOVE) {
                found.add(new Bubble(trough, troughAt, price, tick,
                        (tick - troughAt) / (double) TICKS_PER_DAY));
                falling = false;
                trough = Integer.MAX_VALUE;
            }
        }
        return found;
    }

    /**
     * Every leg of the price's wandering, from one turning point to the next.
     *
     * <p>A turning point is where the price reverses by at least {@code minimumMove}, which
     * keeps the ordinary wobble from being counted as a change of mind. The caller supplies
     * it because this class does not know what normal is; a tenth of the base price is the
     * usual choice and is what the dashboard passes.
     *
     * <p>This is the measure E31 wanted and did not have. Five bubbles in one run says the
     * village bubbled five times; the swings say whether each one was smaller than the last.
     */
    public List<Swing> swings(int minimumMove) {
        List<Swing> found = new ArrayList<>();
        if (priceSeries.size() < 2) {
            return found;
        }
        long turnTick = priceSeries.get(0)[0];
        int turnPrice = priceSeries.get(0)[1];
        long highTick = turnTick;
        int highPrice = turnPrice;
        long lowTick = turnTick;
        int lowPrice = turnPrice;
        // Which way the price is currently travelling: 1 up, -1 down, 0 until it has moved
        // far enough to have made up its mind. Both reversals are live while it is 0, and
        // whichever happens first decides.
        int direction = 0;

        for (int[] point : priceSeries) {
            long tick = point[0];
            int price = point[1];
            if (price > highPrice) {
                highPrice = price;
                highTick = tick;
            }
            if (price < lowPrice) {
                lowPrice = price;
                lowTick = tick;
            }

            if (direction >= 0 && highPrice - price >= minimumMove) {
                // It climbed to the high and has since come back this far: the high was a
                // turning point, and the leg up to it is finished.
                if (highPrice != turnPrice) {
                    found.add(new Swing(turnTick, highTick, turnPrice, highPrice));
                }
                turnTick = highTick;
                turnPrice = highPrice;
                direction = -1;
                highTick = lowTick = tick;
                highPrice = lowPrice = price;
            } else if (direction <= 0 && price - lowPrice >= minimumMove) {
                if (lowPrice != turnPrice) {
                    found.add(new Swing(turnTick, lowTick, turnPrice, lowPrice));
                }
                turnTick = lowTick;
                turnPrice = lowPrice;
                direction = 1;
                highTick = lowTick = tick;
                highPrice = lowPrice = price;
            }
        }
        return found;
    }

    /**
     * How much each leg shrinks against the one before it, averaged over the run.
     *
     * <p>Below 1 the village is calming down; above 1 it is winding up; at 1 it oscillates
     * for ever. Averaged geometrically, because these are ratios and a run that halves and
     * then doubles has gone nowhere, which an arithmetic mean would report as growth.
     *
     * <p><strong>Only comparable between runs of the same length.</strong> E36 measured the
     * same model at 2.22 over ten days, 1.37 over fifty and 1.07 over a hundred and
     * seventy-five: early swings are small while the rumour is still building, so the ratio
     * starts high and only means anything once there are enough swings to average. Two
     * published comparisons were withdrawn for ignoring this.
     *
     * @return empty when there are fewer than {@link #ENOUGH_SWINGS} legs, since a ratio
     *         between a handful of noisy numbers is not a measurement
     */
    /**
     * How many legs a run needs before the ratio between them is worth quoting. Below this
     * the figure is dominated by how early in the run you happened to look.
     */
    public static final int ENOUGH_SWINGS = 10;

    public java.util.OptionalDouble swingDecay(int minimumMove) {
        List<Swing> legs = swings(minimumMove);
        if (legs.size() < ENOUGH_SWINGS) {
            return java.util.OptionalDouble.empty();
        }
        double logSum = 0;
        int counted = 0;
        for (int i = 1; i < legs.size(); i++) {
            int before = legs.get(i - 1).amplitude();
            int after = legs.get(i).amplitude();
            if (before > 0 && after > 0) {
                logSum += Math.log(after / (double) before);
                counted++;
            }
        }
        return counted == 0 ? java.util.OptionalDouble.empty()
                : java.util.OptionalDouble.of(Math.exp(logSum / counted));
    }

    /**
     * The tick after which the price never again leaves the given band around normal.
     *
     * <p>Measured from the end backwards, so it answers "when did it settle and stay
     * settled", not "when did it first touch normal on its way past". A village still
     * swinging when the run ends never settled, and reports empty rather than a number
     * that would read as though it had.
     *
     * @param tolerance how far from normal still counts as settled, as a share: 0.10 is 10%
     */
    public java.util.OptionalLong settledAt(int basePrice, double tolerance) {
        double allowed = basePrice * tolerance;
        long settled = -1;
        for (int i = priceSeries.size() - 1; i >= 0; i--) {
            if (Math.abs(priceSeries.get(i)[1] - basePrice) > allowed) {
                break;
            }
            settled = priceSeries.get(i)[0];
        }
        return settled < 0 ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(settled);
    }

    /**
     * How often a village bubbles, per hundred days, by the one definition in
     * {@link Bubble}. For a village nobody lied to, this is the rate of bubbles that
     * started on their own.
     */
    public double bubblesPerHundredDays() {
        return daily.isEmpty() ? 0 : bubbles().size() * 100.0 / daily.size();
    }

    /**
     * How often somebody starts holding the claim in a village where nobody held it the day
     * before, per hundred days.
     *
     * <p>A diagnostic, not a bubble. It catches the mechanism firing — a villager reading
     * something into the price — long before anyone is convinced enough to move a market,
     * and most onsets come to nothing. Quote {@link #bubblesPerHundredDays()} for how often
     * a village talks itself into a bubble; quote this for how often it starts to.
     */
    public double beliefOnsetsPerHundredDays() {
        if (daily.isEmpty()) {
            return 0;
        }
        int onsets = 0;
        int previousHolders = 0;
        for (DayOfTrading day : daily) {
            if (day.heard() > 0 && previousHolders == 0) {
                onsets++;
            }
            previousHolders = day.heard();
        }
        return onsets * 100.0 / daily.size();
    }

    /**
     * Whether a bubble began within the given number of days after a moment.
     *
     * <p>A run long enough will wander into a bubble eventually whether or not anybody
     * lied, so asking whether one ever happened says more about the length of the run than
     * about the lie. This asks whether one happened while the lie was still fresh.
     */
    public java.util.Optional<Bubble> bubbleWithin(long afterTick, int days) {
        java.util.Optional<Bubble> found = bubble();
        if (found.isEmpty()) {
            return found;
        }
        long deadline = afterTick + (long) days * TICKS_PER_DAY;
        Bubble bubble = found.get();
        return bubble.peakTick() >= afterTick && bubble.peakTick() <= deadline
                ? found
                : java.util.Optional.empty();
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
