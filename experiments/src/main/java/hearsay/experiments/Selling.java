package hearsay.experiments;

import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.Estimate;
import hearsay.Event;
import hearsay.Good;
import hearsay.Input;
import hearsay.MarketStats;
import hearsay.Params;
import hearsay.PlantRumor;
import hearsay.PlayerTraded;
import hearsay.Run;
import hearsay.Simulation;
import hearsay.Target;
import hearsay.VillagerMoved;
import hearsay.Villager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NavigableSet;
import java.util.Random;
import java.util.TreeSet;

/**
 * Selling in bulk: the measurement stage 2's plan owed (E45).
 *
 * <p>Evidence from a sale is weighed by its value in emeralds and saturates at 128, which is
 * new with stage 2: before it, a sale was counted in diamonds. The question is the one the
 * plan named — can a player who sells as much as a counter will take, every day, start a
 * panic in a village nobody lied to? — asked of every good a villager buys.
 *
 * <p>Headless runs have no player, so the sales are scripted: once a village day, at the
 * tick the market is busiest, at one counter or three, as much as a counter takes before it
 * restocks, witnessed by everyone standing in the market. That is the generous end of what a
 * player can do, which is the right end for a guarantee.
 *
 * <p>Where people stand is decided by the movement stream, which reads no belief, so the
 * village is run once unsold to find who is in the market, and again with the sales. The
 * experiment checks that every move is identical between the two rather than assuming it.
 *
 * <pre>./gradlew :experiments:selling</pre>
 */
public final class Selling {

    private static final int QUIET_RUNS = 240;
    private static final int QUIET_TICKS = 700; // 175 days, the goods gate's length
    private static final int LIED_RUNS = 100;
    private static final int LIED_TICKS = 200;
    private static final int WITHIN_DAYS = 30;
    private static final long FIRST_SEED = 7001;

    /** Vanilla's restock limit, which the plugin's counters keep. */
    private static final int MOST_A_COUNTER_TAKES = 12;

    /** Selling starts on the third day, once the lie has had a day to spread. */
    private static final int FIRST_SELLING_DAY = 3;

    private static final Good[] SOLD = {Good.DIAMOND, Good.GOLD, Good.IRON, Good.WHEAT};

    /**
     * The gate, written before the first run: one comparison per good, sharing the 5% (E42),
     * at three counters a day. That turned out to be the wrong pace to judge at — pinned to
     * the floor, a price rarely rebounds — so it is judged at one counter a day too, and all
     * eight comparisons share the 5%. Both are printed; E45 reports both.
     */
    private static final Target.SameAs NO_PANIC_FROM_SELLING = Target.sameAs(
            "bursts per 100 village-days with nobody lying, sold into as hard as possible, "
                    + "as when left alone", QUIET_RUNS);

    /** How a player sells: bundles per sale, and sales at different counters per day. */
    record Pace(String name, int bundles, int counters) {
        boolean sells() {
            return bundles > 0;
        }
    }

    private static final Pace NONE = new Pace("nobody sells", 0, 0);
    private static final Pace ONE = new Pace("1 bundle a day", 1, 1);
    private static final Pace FULL = new Pace("12 bundles a day", MOST_A_COUNTER_TAKES, 1);
    private static final Pace HARDEST = new Pace("12 at three counters a day",
            MOST_A_COUNTER_TAKES, 3);

    private Selling() {
    }

    public static void main(String[] args) {
        System.out.println("Selling in bulk, every good a villager buys, seeds from " + FIRST_SEED);
        System.out.println();

        List<String> gate = new ArrayList<>();
        for (Good good : SOLD) {
            System.out.println("== " + good.id() + " — a bundle is " + good.amount(good.bundle())
                    + " for " + good.normalEmeralds() + " emeralds");
            Params params = Params.defaults().withGoods(good);
            Quiet alone = quiet(params, good, NONE);
            Lied untouched = lied(params, good, NONE);
            print(NONE, alone, untouched, null, null);
            for (Pace selling : List.of(ONE, FULL, HARDEST)) {
                Quiet sold = quiet(params, good, selling);
                Lied liedSold = lied(params, good, selling);
                print(selling, sold, liedSold, alone, untouched);
                if (selling == HARDEST || selling == FULL) {
                    Target.Verdict verdict = NO_PANIC_FROM_SELLING.judge(
                            sold.bursts, alone.bursts, 2 * SOLD.length);
                    gate.add(good.id() + ", " + selling.name() + ": " + verdict);
                }
            }
            // How much room the adopted witness weight leaves, at the hardest selling.
            for (double witness : new double[] {0.07, 0.28}) {
                Params swept = params.withWitnessWeight(witness);
                Quiet sold = quiet(swept, good, HARDEST);
                System.out.printf(Locale.ROOT, "  witnessWeight %.2f, hardest: quiet bursts %s%n",
                        witness, sold.bursts);
            }
            System.out.println();
        }
        System.out.println("The gate, at the adopted weights (tradeWeight "
                + Params.TRADE_WEIGHT + ", witnessWeight " + Params.WITNESS_WEIGHT + "):");
        gate.forEach(System.out::println);
    }

    /** Villages nobody lied to. */
    private record Quiet(Estimate bursts, Estimate busts, Estimate lastQuarter, int lowest,
                         int movesDiffered, Estimate burstBy30, Estimate burstBy60,
                         Estimate bustBy30) {
    }

    private static Quiet quiet(Params params, Good good, Pace selling) {
        Claim scarce = new Claim(good.id(), ClaimType.SCARCE);
        int[] bursts = new int[QUIET_RUNS];
        int[] busts = new int[QUIET_RUNS];
        double[] lastQuarter = new double[QUIET_RUNS];
        int lowest = Integer.MAX_VALUE;
        int movesDiffered = 0;
        int by30 = 0;
        int by60 = 0;
        int bustBy30 = 0;
        for (int i = 0; i < QUIET_RUNS; i++) {
            long seed = FIRST_SEED + i;
            List<Input> sales = sales(seed, params, good, selling, List.of(), QUIET_TICKS);
            Run run = Run.execute(seed, params, sales, QUIET_TICKS);
            if (selling.sells() && !sameMoves(run, Run.execute(seed, params, List.of(), QUIET_TICKS))) {
                movesDiffered++;
            }
            MarketStats stats = MarketStats.of(run.log(), scarce);
            bursts[i] = stats.bubbles().size();
            // How soon, since a played session lasts weeks and these run for half a year.
            long first = stats.bubbles().stream().mapToLong(b -> b.peakTick()).min().orElse(Long.MAX_VALUE);
            by30 += first <= 30 * 4 ? 1 : 0;
            by60 += first <= 60 * 4 ? 1 : 0;
            bustBy30 += stats.busts().stream().anyMatch(b -> b.peakTick() <= 30 * 4) ? 1 : 0;
            busts[i] = stats.busts().size();
            lastQuarter[i] = lastQuarterPrice(run, good);
            lowest = Math.min(lowest, lowestPrice(run, good));
        }
        return new Quiet(Estimate.ratePer(bursts, QUIET_TICKS / 4.0, 100),
                Estimate.ratePer(busts, QUIET_TICKS / 4.0, 100), Estimate.mean(lastQuarter),
                lowest, movesDiffered, Estimate.proportion(by30, QUIET_RUNS),
                Estimate.proportion(by60, QUIET_RUNS), Estimate.proportion(bustBy30, QUIET_RUNS));
    }

    /** Villages lied to on the first tick, sold into from the third day. */
    private record Lied(Estimate burstWithin30, double meanPeak) {
    }

    private static Lied lied(Params params, Good good, Pace selling) {
        Claim scarce = new Claim(good.id(), ClaimType.SCARCE);
        int burst = 0;
        double peaks = 0;
        for (int i = 0; i < LIED_RUNS; i++) {
            long seed = FIRST_SEED + i;
            int planter = Run.execute(seed, params, List.of(), 1).finalState()
                    .gossipiestVillager().id();
            List<Input> lie = List.of(new PlantRumor(1, scarce, 1, planter));
            List<Input> inputs = new ArrayList<>(lie);
            inputs.addAll(sales(seed, params, good, selling, lie, LIED_TICKS));
            inputs.sort(java.util.Comparator.comparingLong(Input::tick));
            MarketStats stats = MarketStats.of(
                    Run.execute(seed, params, inputs, LIED_TICKS).log(), scarce);
            burst += stats.bubbleWithin(1, WITHIN_DAYS).isPresent() ? 1 : 0;
            peaks += stats.peakPrice();
        }
        return new Lied(Estimate.proportion(burst, LIED_RUNS), peaks / LIED_RUNS);
    }

    /**
     * The scripted player: each day from the third, at the tick the market is busiest, a
     * sale at each of {@code counters} different villagers standing in it. Nothing is sold on
     * a day the market never reaches its quorum, since the plugin's counters would be empty.
     *
     * <p>The run it reads is the one with the other inputs but no sales, and the moves are
     * checked afterwards to be the same with them.
     */
    static List<Input> sales(long seed, Params params, Good good, Pace selling,
                                     List<Input> others, int ticks) {
        List<Input> sales = new ArrayList<>();
        if (!selling.sells()) {
            return sales;
        }
        Random choosing = new Random(seed * 31 + good.ordinal()); // the player, not the village
        Simulation village = new Simulation(seed, params, others);
        List<NavigableSet<Integer>> present = new ArrayList<>();
        present.add(new TreeSet<>()); // tick 0
        for (int tick = 1; tick <= ticks; tick++) {
            village.step();
            NavigableSet<Integer> here = new TreeSet<>();
            for (Villager villager : village.state().villagers().values()) {
                if (villager.inTheMarket(tick, params.marketWindowTicks())) {
                    here.add(villager.id());
                }
            }
            present.add(here);
        }
        for (int day = FIRST_SELLING_DAY; (day + 1) * 4 < ticks; day++) {
            int busiest = -1;
            for (int tick = day * 4; tick < day * 4 + 4; tick++) {
                if (busiest < 0 || present.get(tick).size() > present.get(busiest).size()) {
                    busiest = tick;
                }
            }
            NavigableSet<Integer> watching = present.get(busiest);
            if (watching.size() < params.marketQuorum()) {
                continue;
            }
            List<Integer> counters = new ArrayList<>(watching);
            java.util.Collections.shuffle(counters, choosing);
            for (int c = 0; c < Math.min(selling.counters(), counters.size()); c++) {
                sales.add(new PlayerTraded(busiest + 1, counters.get(c), good.id(),
                        selling.bundles() * good.bundle(),
                        selling.bundles() * good.normalEmeralds(), watching));
            }
        }
        return sales;
    }

    private static boolean sameMoves(Run a, Run b) {
        return moves(a).equals(moves(b));
    }

    private static List<Event> moves(Run run) {
        List<Event> moves = new ArrayList<>();
        for (Event event : run.log()) {
            if (event instanceof VillagerMoved) {
                moves.add(event);
            }
        }
        return moves;
    }

    private static List<Integer> prices(Run run, Good good) {
        List<Integer> prices = new ArrayList<>();
        for (Event event : run.log()) {
            if (event instanceof hearsay.MarketPriceSet set && set.item().equals(good.id())) {
                prices.add(set.price());
            }
        }
        return prices;
    }

    private static double lastQuarterPrice(Run run, Good good) {
        List<Integer> prices = prices(run, good);
        List<Integer> last = prices.subList(prices.size() * 3 / 4, prices.size());
        return last.stream().mapToInt(Integer::intValue).average().orElse(Double.NaN);
    }

    private static int lowestPrice(Run run, Good good) {
        return prices(run, good).stream().mapToInt(Integer::intValue).min().orElse(Integer.MAX_VALUE);
    }

    private static void print(Pace selling, Quiet quiet, Lied lied, Quiet alone, Lied untouched) {
        System.out.printf(Locale.ROOT,
                "  %-28s quiet bursts/100d %s, a burst by day 30 %.1f%%, by day 60 %.1f%% | busts/100d %s, a bust by day 30 %.0f%% | lowest %d | last quarter %.1f | lie bursts %s, peak %.0f%s%n",
                selling.name(), fmt(quiet.bursts()), 100 * quiet.burstBy30().value(),
                100 * quiet.burstBy60().value(), fmt(quiet.busts()), 100 * quiet.bustBy30().value(),
                quiet.lowest(), quiet.lastQuarter().value(), fmt(lied.burstWithin30()), lied.meanPeak(),
                quiet.movesDiffered() > 0 ? "  (moves differed in " + quiet.movesDiffered() + " runs)" : "");
    }

    private static String fmt(Estimate estimate) {
        return String.format(Locale.ROOT, "%.3f [%.3f, %.3f]", estimate.value(), estimate.low(),
                estimate.high());
    }
}
