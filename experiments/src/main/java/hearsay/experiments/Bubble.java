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
import java.util.List;
import java.util.Map;

/**
 * The experiment the project rests on. Each setting is run three ways over the same seeds:
 *
 * <ul>
 *   <li><b>rumor + feedback</b> — a rumor is planted and the market can feed back into belief</li>
 *   <li><b>rumor only</b> — the same rumor with the feedback switched off, which is what the
 *       rumor does on its own</li>
 *   <li><b>quiet</b> — nothing planted, so anything that happens came out of the noise</li>
 * </ul>
 *
 * <p>Movement and gossip draw from streams that inputs never touch, so all three put the
 * same villagers in the same places on the same ticks. The differences between the columns
 * are the rumor and the loop, and nothing else.
 *
 * <pre>
 * ./gradlew :experiments:bubble --args="--seeds 50 --ticks 200 \
 *     --observation 0.05,0.10,0.15,0.20,0.25,0.30 --sensitivity 0.5,0.75,1.0,1.25,1.5 \
 *     --noise 0.030 --decay 0.86 --csv bubble.csv"
 * </pre>
 */
public final class Bubble {

    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    /** A bubble is one that ran up past this... */
    private static final int BURST_PEAK_ABOVE = 130;

    /** ...and then came back under this before the run ended. */
    private static final int BURST_BACK_BELOW = 110;

    public static void main(String[] args) throws IOException {
        Map<String, String> options = Cli.parse(args);
        if (options.containsKey("help")) {
            System.out.println(USAGE);
            return;
        }

        int seeds = Cli.intOption(options, "seeds", 50);
        long firstSeed = Cli.longOption(options, "first-seed", 1);
        int ticks = Cli.intOption(options, "ticks", 200);
        List<Double> observation = Cli.doubles(
                options.getOrDefault("observation", "0.05,0.10,0.15,0.20,0.25,0.30"));
        List<Double> sensitivity = Cli.doubles(
                options.getOrDefault("sensitivity", "0.5,0.75,1.0,1.25,1.5"));
        double noise = Cli.doubleOption(options, "noise", Params.defaults().marketNoise());
        double noiseDecay = Cli.doubleOption(options, "decay", Params.defaults().noiseDecay());
        Path csv = Path.of(options.getOrDefault("csv", "bubble.csv"));

        System.out.printf("Bubble: %d seeds (%d..%d), %d ticks, %d settings, each run three ways%n",
                seeds, firstSeed, firstSeed + seeds - 1, ticks,
                observation.size() * sensitivity.size());
        System.out.printf("Market noise %.3f carried at %.2f. A bubble peaks above %d "
                + "and comes back under %d.%n", noise, noiseDecay, BURST_PEAK_ABOVE, BURST_BACK_BELOW);
        System.out.println();

        Map<Long, Integer> planters = new HashMap<>();
        for (long seed = firstSeed; seed < firstSeed + seeds; seed++) {
            planters.put(seed, gossipiestVillager(seed));
        }

        List<Row> table = new ArrayList<>();
        for (double weight : observation) {
            for (double sens : sensitivity) {
                Params withLoop = Params.defaults()
                        .withObservationWeight(weight)
                        .withPriceSensitivity(sens)
                        .withMarketNoise(noise)
                        .withNoiseDecay(noiseDecay);
                Params noLoop = withLoop.withObservationWeight(0);

                Condition rumorAndFeedback = new Condition();
                Condition rumorOnly = new Condition();
                Condition quiet = new Condition();
                for (long seed = firstSeed; seed < firstSeed + seeds; seed++) {
                    List<Input> rumor = List.of(
                            new PlantRumor(1, DIAMONDS_SCARCE, 1, planters.get(seed)));
                    rumorAndFeedback.add(measure(seed, withLoop, ticks, rumor));
                    rumorOnly.add(measure(seed, noLoop, ticks, rumor));
                    quiet.add(measure(seed, withLoop, ticks, List.of()));
                }
                table.add(new Row(weight, sens, rumorAndFeedback, rumorOnly, quiet));
            }
        }

        print(table);
        writeCsv(csv, table, seeds, firstSeed, ticks, noise, noiseDecay);
        System.out.println();
        System.out.println("Wrote " + csv.toAbsolutePath());
    }

    private static MarketStats measure(long seed, Params params, int ticks, List<Input> inputs) {
        return MarketStats.of(Run.execute(seed, params, inputs, ticks).log(), DIAMONDS_SCARCE);
    }

    private static final class Condition {
        private int seeds;
        private int halfBelieving;
        private int anyHolder;
        private int burst;
        private long totalPeakPrice;
        private double totalBurstDays;

        void add(MarketStats stats) {
            seeds++;
            if (stats.reachedHalfBelieving()) {
                halfBelieving++;
            }
            if (stats.peakHolders() > 0) {
                anyHolder++;
            }
            stats.burst(BURST_PEAK_ABOVE, BURST_BACK_BELOW).ifPresent(b -> {
                burst++;
                totalBurstDays += b.days();
            });
            totalPeakPrice += stats.peakPrice();
        }

        double shareHalfBelieving() { return halfBelieving / (double) seeds; }
        double shareAnyHolder() { return anyHolder / (double) seeds; }
        double shareBurst() { return burst / (double) seeds; }
        double meanPeakPrice() { return totalPeakPrice / (double) seeds; }
        double meanBurstDays() { return burst == 0 ? Double.NaN : totalBurstDays / burst; }
    }

    private record Row(double observationWeight, double priceSensitivity,
                       Condition rumorAndFeedback, Condition rumorOnly, Condition quiet) {}

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
        System.out.println("                 rumor + feedback            rumor only       "
                + "        quiet");
        System.out.println("  obs  sens   half  peak$  burst  days |  peak$  burst  days "
                + "|  held  peak$  burst");
        double lastWeight = Double.NaN;
        for (Row row : table) {
            if (!Double.isNaN(lastWeight) && row.observationWeight() != lastWeight) {
                System.out.println();
            }
            lastWeight = row.observationWeight();
            System.out.printf(" %.2f  %.2f  %5s %6.1f %6s %5s |%6.1f %6s %5s |%5s %6.1f %6s%n",
                    row.observationWeight(), row.priceSensitivity(),
                    percent(row.rumorAndFeedback().shareHalfBelieving()),
                    row.rumorAndFeedback().meanPeakPrice(),
                    percent(row.rumorAndFeedback().shareBurst()),
                    days(row.rumorAndFeedback().meanBurstDays()),
                    row.rumorOnly().meanPeakPrice(),
                    percent(row.rumorOnly().shareBurst()),
                    days(row.rumorOnly().meanBurstDays()),
                    percent(row.quiet().shareAnyHolder()),
                    row.quiet().meanPeakPrice(),
                    percent(row.quiet().shareBurst()));
        }
    }

    private static String days(double value) {
        return Double.isNaN(value) ? "-" : String.format("%.1f", value);
    }

    private static String percent(double share) {
        return Math.round(share * 100) + "%";
    }

    private static void writeCsv(Path csv, List<Row> table, int seeds, long firstSeed, int ticks,
                                 double noise, double noiseDecay) throws IOException {
        if (csv.getParent() != null) {
            Files.createDirectories(csv.getParent());
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(csv))) {
            out.println("observationWeight,priceSensitivity,seeds,firstSeed,ticks,marketNoise,"
                    + "noiseDecay,bubblePeakAbove,bubbleBackBelow,"
                    + "loopHalfBelieving,loopPeakPrice,loopBurst,loopBurstDays,"
                    + "rumorOnlyHalfBelieving,rumorOnlyPeakPrice,rumorOnlyBurst,rumorOnlyBurstDays,"
                    + "quietAnyHolder,quietHalfBelieving,quietPeakPrice,quietBurst");
            for (Row row : table) {
                out.printf("%.2f,%.2f,%d,%d,%d,%.3f,%.2f,%d,%d,"
                                + "%.3f,%.2f,%.3f,%.2f,%.3f,%.2f,%.3f,%.2f,%.3f,%.3f,%.2f,%.3f%n",
                        row.observationWeight(), row.priceSensitivity(), seeds, firstSeed, ticks,
                        noise, noiseDecay, BURST_PEAK_ABOVE, BURST_BACK_BELOW,
                        row.rumorAndFeedback().shareHalfBelieving(),
                        row.rumorAndFeedback().meanPeakPrice(),
                        row.rumorAndFeedback().shareBurst(),
                        nanToZero(row.rumorAndFeedback().meanBurstDays()),
                        row.rumorOnly().shareHalfBelieving(), row.rumorOnly().meanPeakPrice(),
                        row.rumorOnly().shareBurst(), nanToZero(row.rumorOnly().meanBurstDays()),
                        row.quiet().shareAnyHolder(), row.quiet().shareHalfBelieving(),
                        row.quiet().meanPeakPrice(), row.quiet().shareBurst());
            }
        }
    }

    private static double nanToZero(double value) {
        return Double.isNaN(value) ? 0 : value;
    }

    private static final String USAGE = """
            Runs the village three ways across a grid of market settings.

              --seeds N         seeds per setting (default 50)
              --first-seed N    the first seed, for validating on seeds never swept over
              --ticks N         ticks per run (default 200)
              --observation L   comma-separated observationWeight values
              --sensitivity L   comma-separated priceSensitivity values
              --noise V         marketNoise (defaults to the Params default)
              --decay V         noiseDecay (defaults to the Params default)
              --csv PATH        where to write the table (default bubble.csv)
            """;
}
