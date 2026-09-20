package hearsay.experiments;

import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.Input;
import hearsay.MarketStats;
import hearsay.Params;
import hearsay.PlantRumor;
import hearsay.Run;
import hearsay.Simulation;
import hearsay.Villager;
import hearsay.WorldState;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The experiment the project rests on: does a planted rumor produce a bubble, and does the
 * village stay calm without one?
 *
 * <p>Every combination is run twice over the same seeds, once with a rumor planted and
 * once with nothing planted at all. Because movement and gossip draw from streams that
 * inputs never touch, the two runs put the same villagers in the same places on the same
 * ticks, so any difference between them is the rumor and nothing else.
 *
 * <pre>
 * ./gradlew :experiments:bubble --args="--seeds 50 --ticks 200 \
 *     --observation 0,0.1,0.15,0.2,0.3 --sensitivity 0.5,1.0,1.5 --csv bubble.csv"
 * </pre>
 */
public final class Bubble {

    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    /** A price this far above base is the visible sign of a bubble. */
    private static final int BUBBLE_PRICE = 120;

    /** A bubble is one that ran up past this... */
    private static final int BURST_PEAK_ABOVE = 130;

    /** ...and then came back under this before the run ended. */
    private static final int BURST_BACK_BELOW = 110;

    public static void main(String[] args) throws IOException {
        Map<String, String> options = parse(args);
        if (options.containsKey("help")) {
            System.out.println(USAGE);
            return;
        }

        int seeds = intOption(options, "seeds", 50);
        long firstSeed = longOption(options, "first-seed", 1);
        int ticks = intOption(options, "ticks", 200);
        List<Double> observation = doubles(options.getOrDefault("observation", "0,0.1,0.15,0.2,0.3"));
        List<Double> sensitivity = doubles(options.getOrDefault("sensitivity", "0.5,1.0,1.5"));
        Path csv = Path.of(options.getOrDefault("csv", "bubble.csv"));

        System.out.printf("Bubble: %d seeds (%d..%d), %d ticks, %d combinations, each run twice%n",
                seeds, firstSeed, firstSeed + seeds - 1, ticks,
                observation.size() * sensitivity.size());
        System.out.println("With a rumor planted in the gossipiest villager, and with nothing planted.");
        System.out.println();

        Map<Long, Integer> planters = new HashMap<>();
        for (long seed = firstSeed; seed < firstSeed + seeds; seed++) {
            planters.put(seed, gossipiestVillager(seed));
        }

        List<Row> table = new ArrayList<>();
        for (double weight : observation) {
            for (double sens : sensitivity) {
                Params params = Params.defaults()
                        .withObservationWeight(weight)
                        .withPriceSensitivity(sens);
                Condition withRumor = new Condition();
                Condition without = new Condition();
                for (long seed = firstSeed; seed < firstSeed + seeds; seed++) {
                    withRumor.add(measure(seed, params, ticks,
                            List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, planters.get(seed)))));
                    without.add(measure(seed, params, ticks, List.of()));
                }
                table.add(new Row(weight, sens, withRumor, without));
            }
        }

        print(table);
        writeCsv(csv, table, seeds, firstSeed, ticks);
        System.out.println();
        System.out.println("Wrote " + csv.toAbsolutePath());
    }

    /** One seed under one condition. */
    private static MarketStats measure(long seed, Params params, int ticks, List<Input> inputs) {
        return MarketStats.of(Run.execute(seed, params, inputs, ticks).log(), DIAMONDS_SCARCE);
    }

    /** The seeds of one condition, summarised as they arrive. */
    private static final class Condition {
        private int seeds;
        private int halfBelieving;
        private int aboveBubblePrice;
        private long totalPeakPrice;
        private long totalDaysAbove;
        private long totalPeakBelievers;
        private int burst;
        private double totalBurstDays;

        void add(MarketStats stats) {
            seeds++;
            stats.burst(BURST_PEAK_ABOVE, BURST_BACK_BELOW).ifPresent(b -> {
                burst++;
                totalBurstDays += b.days();
            });
            if (stats.reachedHalfBelieving()) {
                halfBelieving++;
            }
            int daysAbove = stats.daysAbove(BUBBLE_PRICE);
            if (daysAbove > 0) {
                aboveBubblePrice++;
            }
            totalPeakPrice += stats.peakPrice();
            totalDaysAbove += daysAbove;
            totalPeakBelievers += stats.peakBelievers();
        }

        double shareHalfBelieving() { return halfBelieving / (double) seeds; }
        double shareAboveBubblePrice() { return aboveBubblePrice / (double) seeds; }
        double meanPeakPrice() { return totalPeakPrice / (double) seeds; }
        double meanDaysAbove() { return totalDaysAbove / (double) seeds; }
        double meanPeakBelievers() { return totalPeakBelievers / (double) seeds; }
        double shareBurst() { return burst / (double) seeds; }
        double meanBurstDays() { return burst == 0 ? Double.NaN : totalBurstDays / burst; }
    }

    private record Row(double observationWeight, double priceSensitivity,
                       Condition withRumor, Condition without) {}

    private static int gossipiestVillager(long seed) {
        WorldState village = Run.execute(seed, Params.defaults(), List.of(), 1).finalState();
        int gossipiest = 0;
        for (Villager villager : village.villagers().values()) {
            if (villager.traits().gossip() > village.villager(gossipiest).traits().gossip()) {
                gossipiest = villager.id();
            }
        }
        return gossipiest;
    }

    private static void print(List<Row> table) {
        System.out.println("                         with a planted rumor                    "
                + "     without any rumor");
        System.out.println("   obs   sens   half  peak$  burst  burst-days  believers "
                + "|  half  peak$  burst  believers");
        double lastWeight = Double.NaN;
        for (Row row : table) {
            if (!Double.isNaN(lastWeight) && row.observationWeight() != lastWeight) {
                System.out.println();
            }
            lastWeight = row.observationWeight();
            System.out.printf("  %.2f  %.1f   %5s %6.1f %6s %11s %10.1f |%6s %6.1f %6s %10.1f%n",
                    row.observationWeight(), row.priceSensitivity(),
                    percent(row.withRumor().shareHalfBelieving()),
                    row.withRumor().meanPeakPrice(),
                    percent(row.withRumor().shareBurst()),
                    days(row.withRumor().meanBurstDays()),
                    row.withRumor().meanPeakBelievers(),
                    percent(row.without().shareHalfBelieving()),
                    row.without().meanPeakPrice(),
                    percent(row.without().shareBurst()),
                    row.without().meanPeakBelievers());
        }
    }

    private static String days(double value) {
        return Double.isNaN(value) ? "-" : String.format("%.1f", value);
    }

    private static String percent(double share) {
        return Math.round(share * 100) + "%";
    }

    private static void writeCsv(Path csv, List<Row> table, int seeds, long firstSeed, int ticks)
            throws IOException {
        if (csv.getParent() != null) {
            Files.createDirectories(csv.getParent());
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(csv))) {
            out.println("observationWeight,priceSensitivity,seeds,firstSeed,ticks,bubblePrice,"
                    + "rumorShareHalfBelieving,rumorShareAbovePrice,rumorMeanPeakPrice,"
                    + "rumorMeanDaysAbove,rumorMeanPeakBelievers,rumorShareBurst,rumorMeanBurstDays,"
                    + "quietShareHalfBelieving,quietShareAbovePrice,quietMeanPeakPrice,"
                    + "quietMeanDaysAbove,quietMeanPeakBelievers,quietShareBurst,quietMeanBurstDays");
            for (Row row : table) {
                out.printf("%.2f,%.2f,%d,%d,%d,%d,%.3f,%.3f,%.2f,%.3f,%.2f,%.3f,%.2f,"
                                + "%.3f,%.3f,%.2f,%.3f,%.2f,%.3f,%.2f%n",
                        row.observationWeight(), row.priceSensitivity(), seeds, firstSeed, ticks,
                        BUBBLE_PRICE,
                        row.withRumor().shareHalfBelieving(), row.withRumor().shareAboveBubblePrice(),
                        row.withRumor().meanPeakPrice(), row.withRumor().meanDaysAbove(),
                        row.withRumor().meanPeakBelievers(),
                        row.withRumor().shareBurst(), row.withRumor().meanBurstDays(),
                        row.without().shareHalfBelieving(), row.without().shareAboveBubblePrice(),
                        row.without().meanPeakPrice(), row.without().meanDaysAbove(),
                        row.without().meanPeakBelievers(),
                        row.without().shareBurst(), row.without().meanBurstDays());
            }
        }
    }

    private static final String USAGE = """
            Runs the village with and without a planted rumor across a grid of market settings.

              --seeds N         seeds per combination (default 50)
              --first-seed N    the first seed (default 1)
              --ticks N         ticks per run (default 200, which is 50 days)
              --observation L   comma-separated observationWeight values
              --sensitivity L   comma-separated priceSensitivity values
              --csv PATH        where to write the table (default bubble.csv)
            """;

    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("Expected an option, got " + args[i]);
            }
            String key = args[i].substring(2);
            if (key.equals("help")) {
                options.put(key, "");
            } else if (i + 1 < args.length) {
                options.put(key, args[++i]);
            } else {
                throw new IllegalArgumentException("Option --" + key + " needs a value");
            }
        }
        return options;
    }

    private static int intOption(Map<String, String> options, String key, int fallback) {
        return options.containsKey(key) ? Integer.parseInt(options.get(key)) : fallback;
    }

    private static long longOption(Map<String, String> options, String key, long fallback) {
        return options.containsKey(key) ? Long.parseLong(options.get(key)) : fallback;
    }

    private static List<Double> doubles(String list) {
        List<Double> values = new ArrayList<>();
        for (String part : list.split(",")) {
            values.add(Double.parseDouble(part.trim()));
        }
        return values;
    }
}
