package hearsay.experiments;

import hearsay.Belief;
import hearsay.Bubble;
import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.Good;
import hearsay.Input;
import hearsay.MarketStats;
import hearsay.Params;
import hearsay.PlantRumor;
import hearsay.Run;
import hearsay.Simulation;
import hearsay.Villager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * E47: only the part of a price move on the claim's side of normal is evidence for it,
 * swept off and on, as the fix for E45's glut-rebound panics.
 *
 * <p>The same measures as E46, and one more asked for by name: a village recovering from
 * heavy selling that crosses above normal on the way back — does that overshoot grow into a
 * false scarcity bubble the size of a real lie's, or stay smaller, with nothing but the
 * villagers' own anchors to hold it up? Compared on the same villages at the same moment:
 * sold into hard for a month and then left alone, against left alone and lied to.
 *
 * <pre>./gradlew :experiments:level</pre>
 */
public final class LevelSweep {

    private static final Claim DIAMONDS_SCARCE = new Claim(Good.DIAMOND.id(), ClaimType.SCARCE);

    /** The month of selling: a counter's full restock daily, days 3 to 30. */
    private static final int SELLING_ENDS = 120;
    /** The month after, in which an overshoot or a lie's bubble is looked for. */
    private static final int WINDOW_ENDS = SELLING_ENDS + 120;
    /** Run on past the window, so a bubble peaking late still has time to come back. */
    private static final int RUN_ENDS = WINDOW_ENDS + 80;
    private static final int VILLAGES = 200;

    private LevelSweep() {
    }

    public static void main(String[] args) {
        for (double gate : new double[] {0.0, 1.0}) {
            Params params = Params.defaults().withLevelGate(gate);
            System.out.println("== levelGate " + gate + (gate == 0 ? " (off, today)" : " (on)"));
            TrendSweep.calibration(params);
            TrendSweep.village(params);
            overshoot(params);
            for (Good good : TrendSweep.SOLD) {
                TrendSweep.selling(Params.defaults().withGoods(good).withLevelGate(gate), good);
            }
            System.out.println();
        }
    }

    /** What happened in the month after the moment, in one village. */
    private record Month(boolean burst, int peakPrice, int peakBelievers, boolean crossed) {
    }

    private static void overshoot(Params params) {
        List<Month> recovering = new ArrayList<>();
        List<Month> lied = new ArrayList<>();
        int crashed = 0;
        List<Integer> lowestPrices = new ArrayList<>();
        for (int i = 0; i < VILLAGES; i++) {
            long seed = 7001 + i;
            List<Input> sales = Selling.sales(seed, params, Good.DIAMOND,
                    new Selling.Pace("12 a day", 12, 1), List.of(), SELLING_ENDS).stream()
                    .filter(s -> s.tick() <= SELLING_ENDS).toList();
            // Fell under a bust's trough while the selling went on. Not "busted": a bust has
            // to come back above 95, and a price that stays down while the player keeps
            // selling never does.
            Run sold = Run.execute(seed, params, sales, SELLING_ENDS);
            int lowest = sold.log().stream()
                    .filter(e -> e instanceof hearsay.MarketPriceSet)
                    .mapToInt(e -> ((hearsay.MarketPriceSet) e).price()).min().orElse(100);
            crashed += lowest < Bubble.TROUGH_BELOW ? 1 : 0;
            lowestPrices.add(lowest);
            recovering.add(month(seed, params, sales));

            int planter = Run.execute(seed, params, List.of(), SELLING_ENDS).finalState()
                    .gossipiestVillager().id();
            lied.add(month(seed, params,
                    List.of(new PlantRumor(SELLING_ENDS + 1, DIAMONDS_SCARCE, 1, planter))));
        }
        int[] lows = lowestPrices.stream().mapToInt(Integer::intValue).sorted().toArray();
        System.out.printf(Locale.ROOT, "  Overshoot: %d villages sold into for a month (%d fell under %d; "
                + "lowest price median %d), then left alone; beside the same villages lied to at "
                + "that moment. The month after:%n",
                VILLAGES, crashed, Bubble.TROUGH_BELOW, lows[lows.length / 2]);
        describe("recovering from selling", recovering);
        describe("lied to", lied);
        describe("recovering, and crossed normal", recovering.stream().filter(Month::crossed).toList());
    }

    private static Month month(long seed, Params params, List<Input> inputs) {
        Simulation village = new Simulation(seed, params, inputs);
        int peakPrice = 0;
        int peakBelievers = 0;
        boolean crossed = false;
        for (int tick = 1; tick <= RUN_ENDS; tick++) {
            village.step();
            if (tick <= SELLING_ENDS || tick > WINDOW_ENDS) {
                continue;
            }
            int price = village.state().marketPrice(Good.DIAMOND).orElse(params.basePrice());
            peakPrice = Math.max(peakPrice, price);
            crossed |= price > params.basePrice();
            int believers = 0;
            for (Villager villager : village.state().villagers().values()) {
                Belief held = villager.belief(DIAMONDS_SCARCE);
                believers += held != null && held.confidence() >= 0.5 ? 1 : 0;
            }
            peakBelievers = Math.max(peakBelievers, believers);
        }
        boolean burst = false;
        for (Bubble bubble : MarketStats.of(village.log(), DIAMONDS_SCARCE).bubbles()) {
            burst |= bubble.peakTick() > SELLING_ENDS && bubble.peakTick() <= WINDOW_ENDS;
        }
        return new Month(burst, peakPrice, peakBelievers, crossed);
    }

    private static void describe(String who, List<Month> months) {
        if (months.isEmpty()) {
            System.out.printf(Locale.ROOT, "    %-32s none%n", who);
            return;
        }
        int[] peaks = months.stream().mapToInt(Month::peakPrice).sorted().toArray();
        int[] believers = months.stream().mapToInt(Month::peakBelievers).sorted().toArray();
        long bursts = months.stream().filter(Month::burst).count();
        System.out.printf(Locale.ROOT,
                "    %-32s n=%3d  burst %5.1f%%  peak price median %d, 90th %d, max %d  |  "
                        + "believers at most: median %d, 90th %d, max %d (of %d)%n",
                who, months.size(), 100.0 * bursts / months.size(),
                peaks[peaks.length / 2], peaks[(int) (peaks.length * 0.9)], peaks[peaks.length - 1],
                believers[believers.length / 2], believers[(int) (believers.length * 0.9)],
                believers[believers.length - 1], Simulation.VILLAGER_COUNT);
    }
}
