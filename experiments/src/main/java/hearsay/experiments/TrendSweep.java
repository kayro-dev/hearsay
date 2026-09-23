package hearsay.experiments;

import hearsay.Bubble;
import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.Estimate;
import hearsay.Event;
import hearsay.Good;
import hearsay.Input;
import hearsay.MarketPriceSet;
import hearsay.MarketStats;
import hearsay.Params;
import hearsay.PlantRumor;
import hearsay.PriceObserved;
import hearsay.Run;
import hearsay.Target;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * E46: reading the price against its own trailing average, swept off, half and full, as the
 * fix for E45's glut-rebound panics.
 *
 * <p>For every setting: E45's bulk selling again, short runs and long; CalibrationTest's three
 * checks exactly as the suite runs them; the goods gate's settling and paired-worlds measures
 * on diamond; and the thing the fix could break — how a lie's bubble deflates, since today
 * the fall from a peak is itself read as evidence of plenty.
 *
 * <pre>./gradlew :experiments:trend</pre>
 */
public final class TrendSweep {

    private record Setting(String name, double anchor, int window) {
        Params apply(Params params) {
            return params.withTrendAnchor(anchor).withTrendWindowTicks(window);
        }
    }

    private static final List<Setting> SETTINGS = List.of(
            new Setting("off", 0.0, Params.TREND_WINDOW_TICKS),
            new Setting("half", 0.5, Params.TREND_WINDOW_TICKS),
            new Setting("full", 1.0, Params.TREND_WINDOW_TICKS),
            // Not asked for: the window is a judgment call, and E45's trace suggested four
            // days is too short to cover a rebound. Fifteen days, at full, to show how much
            // the answer depends on it.
            new Setting("full, 15-day window", 1.0, 60));

    static final Good[] SOLD = {Good.DIAMOND, Good.GOLD, Good.IRON, Good.WHEAT};
    private static final List<Selling.Pace> PACES = List.of(
            new Selling.Pace("1 a day", 1, 1),
            new Selling.Pace("3 a day", 3, 1),
            new Selling.Pace("12 a day", 12, 1),
            new Selling.Pace("12 x 3 counters", 12, 3));

    private static final Claim DIAMONDS_SCARCE = new Claim(Good.DIAMOND.id(), ClaimType.SCARCE);

    // CalibrationTest's own targets, restated so the sweep can say whether it would pass.
    private static final Target.Band A_LIE_BURSTS = Target.demonstrates(
            "a lie bursts the price within 30 days", 0.25, 0.60, 200);
    private static final Target.Band QUIET_VILLAGES = Target.demonstrates(
            "villages nobody lied to bursting within 30 days", 0.0, 0.03, 300);
    private static final Target.Band BACKGROUND_RATE = Target.demonstrates(
            "villages nobody lied to bursting, per 100 village-days over 500 days", 0.0, 0.5, 60);

    private TrendSweep() {
    }

    public static void main(String[] args) {
        List<String[]> rows = new ArrayList<>();
        for (Setting setting : SETTINGS) {
            System.out.println("== " + setting.name());
            Params params = setting.apply(Params.defaults());
            calibration(params);
            village(params);
            for (Good good : SOLD) {
                selling(setting.apply(Params.defaults().withGoods(good)), good);
            }
            System.out.println();
        }
    }

    /** CalibrationTest's three checks, seeds and lengths exactly as the suite runs them. */
    static void calibration(Params params) {
        int burst = 0;
        for (long seed = 1001; seed < 1201; seed++) {
            if (lied(seed, params, 200).bubbleWithin(1, 30).isPresent()) {
                burst++;
            }
        }
        int quiet = 0;
        for (long seed = 1001; seed < 1301; seed++) {
            if (MarketStats.of(Run.execute(seed, params, List.of(), 200).log(), DIAMONDS_SCARCE)
                    .bubbleWithin(1, 30).isPresent()) {
                quiet++;
            }
        }
        int[] lifetime = new int[60];
        for (int i = 0; i < 60; i++) {
            lifetime[i] = MarketStats.of(Run.execute(1001 + i, params, List.of(), 2000).log(),
                    DIAMONDS_SCARCE).bubbles().size();
        }
        System.out.println("  CalibrationTest:");
        System.out.println("    " + A_LIE_BURSTS.judge(Estimate.proportion(burst, 200)));
        System.out.println("    " + QUIET_VILLAGES.judge(Estimate.proportion(quiet, 300)));
        System.out.println("    " + BACKGROUND_RATE.judge(Estimate.ratePer(lifetime, 500, 100)));
    }

    /** The goods gate's measures on diamond, and how a lie's bubble comes down. */
    static void village(Params params) {
        int[] quietCounts = new int[240];
        for (int i = 0; i < 240; i++) {
            quietCounts[i] = MarketStats.of(Run.execute(3001 + i, params, List.of(), 2000).log(),
                    DIAMONDS_SCARCE).bubbles().size();
        }

        List<Double> decays = new ArrayList<>();
        List<Double> fallDays = new ArrayList<>();
        int settled = 0;
        int bubbles = 0;
        int bustsAfter = 0;
        double[] endPrices = new double[200];
        double[] plentyReadings = new double[200];
        double[] peaks = new double[200];
        for (int i = 0; i < 200; i++) {
            long seed = 1001 + i;
            Run run = Run.execute(seed, params, lie(seed, params), 700);
            MarketStats stats = MarketStats.of(run.log(), DIAMONDS_SCARCE);
            stats.swingDecayAfterPeak(10).ifPresent(decays::add);
            settled += stats.settledAt(100, 0.10).isPresent() ? 1 : 0;
            for (Bubble bubble : stats.bubbles()) {
                bubbles++;
                fallDays.add(bubble.days());
                long peak = bubble.peakTick();
                bustsAfter += stats.busts().stream().anyMatch(b -> b.peakTick() > peak
                        && b.peakTick() <= peak + 30 * 4) ? 1 : 0;
            }
            endPrices[i] = lastQuarterPrice(run.log());
            peaks[i] = stats.peakPrice();
            // The fall from a peak read as evidence of plenty: how often it happens at all.
            plentyReadings[i] = run.log().stream().filter(e -> e instanceof PriceObserved o
                    && o.claim().type() == ClaimType.ABUNDANT).count();
        }

        int onlyWith = 0;
        int onlyWithout = 0;
        for (long seed = 2001; seed < 2201; seed++) {
            boolean with = lied(seed, params, 200).bubbleWithin(1, 30).isPresent();
            boolean without = MarketStats.of(Run.execute(seed, params, List.of(), 200).log(),
                    DIAMONDS_SCARCE).bubbleWithin(1, 30).isPresent();
            onlyWith += with && !without ? 1 : 0;
            onlyWithout += without && !with ? 1 : 0;
        }

        double[] falls = fallDays.stream().mapToDouble(Double::doubleValue).toArray();
        double[] sortedFalls = falls.clone();
        Arrays.sort(sortedFalls);
        System.out.println("  Settling and separation (diamond, lie at tick 1, 175 days):");
        System.out.println("    quiet bursts / 100 village-days (240 x 500 days) " + Estimate.ratePer(quietCounts, 500, 100));
        System.out.println("    decay after the largest swing   " + Estimate.mean(decays.stream().mapToDouble(Double::doubleValue).toArray()));
        System.out.println("    settled within 10%              " + Estimate.proportion(settled, 200));
        System.out.println("    last-quarter price              " + Estimate.mean(endPrices));
        System.out.println("    mean peak                       " + Estimate.mean(peaks));
        System.out.println("    paired: only with the lie       " + Estimate.proportion(onlyWith, 200));
        System.out.println("    paired: only without            " + Estimate.proportion(onlyWithout, 200));
        System.out.println("  Deflation:");
        System.out.printf(Locale.ROOT, "    bubbles %d; days from peak back under %d: mean %s, median %.2f%n",
                bubbles, Bubble.BACK_BELOW, falls.length == 0 ? "-" : Estimate.mean(falls),
                sortedFalls.length == 0 ? Double.NaN : sortedFalls[sortedFalls.length / 2]);
        System.out.println("    readings of plenty per run      " + Estimate.mean(plentyReadings));
        System.out.printf(Locale.ROOT, "    a bust within 30 days of a bubble's peak: %d of %d bubbles%n",
                bustsAfter, bubbles);
    }

    /** E45 again: selling-only villages, short and long, and selling into a lie. */
    static void selling(Params params, Good good) {
        Claim scarce = new Claim(good.id(), ClaimType.SCARCE);
        System.out.println("  Selling " + good.id() + ":");
        for (Selling.Pace pace : withNobody()) {
            int[] longBursts = new int[240];
            int shortBursts = 0;
            int shortBusts = 0;
            int lowest = Integer.MAX_VALUE;
            for (int i = 0; i < 240; i++) {
                long seed = 7001 + i;
                List<Input> sales = Selling.sales(seed, params, good, pace, List.of(), 700);
                Run run = Run.execute(seed, params, sales, 700);
                longBursts[i] = MarketStats.of(run.log(), scarce).bubbles().size();
                for (Event e : run.log()) {
                    if (e instanceof MarketPriceSet set && set.item().equals(good.id())) {
                        lowest = Math.min(lowest, set.price());
                    }
                }
                List<Input> shortSales = sales.stream().filter(s -> s.tick() <= 120).toList();
                MarketStats month = MarketStats.of(
                        Run.execute(seed, params, shortSales, 120).log(), scarce);
                shortBursts += month.bubbles().isEmpty() ? 0 : 1;
                shortBusts += month.busts().isEmpty() ? 0 : 1;
            }
            int lieBursts = 0;
            for (int i = 0; i < 100; i++) {
                long seed = 7001 + i;
                List<Input> lie = lie(seed, params, scarce);
                List<Input> inputs = new ArrayList<>(lie);
                inputs.addAll(Selling.sales(seed, params, good, pace, lie, 200));
                inputs.sort(java.util.Comparator.comparingLong(Input::tick));
                lieBursts += MarketStats.of(Run.execute(seed, params, inputs, 200).log(), scarce)
                        .bubbleWithin(1, 30).isPresent() ? 1 : 0;
            }
            System.out.printf(Locale.ROOT,
                    "    %-16s 30-day runs: burst %5.1f%%  bust %5.1f%% | 175-day runs: bursts/100d %s, lowest %d | lie bursts %s%n",
                    pace.name(), 100.0 * shortBursts / 240, 100.0 * shortBusts / 240,
                    Estimate.ratePer(longBursts, 175, 100), lowest,
                    Estimate.proportion(lieBursts, 100));
        }
    }

    private static List<Selling.Pace> withNobody() {
        List<Selling.Pace> all = new ArrayList<>();
        all.add(new Selling.Pace("nobody sells", 0, 0));
        all.addAll(PACES);
        return all;
    }

    private static List<Input> lie(long seed, Params params) {
        return lie(seed, params, DIAMONDS_SCARCE);
    }

    private static List<Input> lie(long seed, Params params, Claim claim) {
        int planter = Run.execute(seed, params, List.of(), 1).finalState().gossipiestVillager().id();
        return List.of(new PlantRumor(1, claim, 1, planter));
    }

    private static MarketStats lied(long seed, Params params, int ticks) {
        return MarketStats.of(Run.execute(seed, params, lie(seed, params), ticks).log(),
                DIAMONDS_SCARCE);
    }

    private static double lastQuarterPrice(List<Event> log) {
        double sum = 0;
        int n = 0;
        for (Event event : log) {
            if (event instanceof MarketPriceSet price && price.item().equals(Good.DIAMOND.id())
                    && price.tick() > 525) {
                sum += price.price();
                n++;
            }
        }
        return n == 0 ? 100 : sum / n;
    }
}
