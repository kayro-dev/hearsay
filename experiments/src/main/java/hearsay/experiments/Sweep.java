package hearsay.experiments;

import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.Input;
import hearsay.Params;
import hearsay.PlantRumor;
import hearsay.Run;
import hearsay.RumorStats;
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
 * Sweeps a grid of settings and reports what the village does under each, so tuning is a
 * decision about a table rather than about one lucky seed.
 *
 * <p>Every figure comes out of {@link RumorStats}, which reads only the event log, so the
 * sweep measures the same thing the dashboard will.
 *
 * <p>Fully determined by its arguments: the same command produces the same table.
 *
 * <pre>
 * ./gradlew :experiments:run --args="--seeds 50 --ticks 200 \
 *     --decay 0.85,0.90,0.93,0.95,0.97 --tell 0.2,0.3,0.4 --csv sweep.csv"
 * </pre>
 */
public final class Sweep {

    private static final Claim DIAMONDS_SCARCE = new Claim("diamond", ClaimType.SCARCE);

    public static void main(String[] args) throws IOException {
        Map<String, String> options = parse(args);
        if (options.containsKey("help")) {
            System.out.println(USAGE);
            return;
        }

        int seeds = intOption(options, "seeds", 50);
        long firstSeed = longOption(options, "first-seed", 1);
        int ticks = intOption(options, "ticks", 200);
        List<Double> decays = doubles(options.getOrDefault("decay", "0.85,0.90,0.93,0.95,0.97"));
        List<Double> thresholds = doubles(options.getOrDefault("tell", "0.2,0.3,0.4"));
        Path csv = Path.of(options.getOrDefault("csv", "sweep.csv"));

        System.out.printf("Sweep: %d seeds (%d..%d), %d ticks, %d combinations%n",
                seeds, firstSeed, firstSeed + seeds - 1, ticks, decays.size() * thresholds.size());
        System.out.println("Planted in each seed's gossipiest villager, one rumor on tick 1.");
        System.out.println();

        // The planter depends only on the seed: traits are drawn from the movement stream,
        // which neither params nor inputs touch. So it is found once and reused across the
        // whole grid.
        Map<Long, Integer> planters = new HashMap<>();
        for (long seed = firstSeed; seed < firstSeed + seeds; seed++) {
            planters.put(seed, gossipiestVillager(seed));
        }

        List<SweepStats.Summary> table = new ArrayList<>();
        for (double decay : decays) {
            for (double threshold : thresholds) {
                List<SweepStats.SeedOutcome> outcomes = new ArrayList<>();
                for (long seed = firstSeed; seed < firstSeed + seeds; seed++) {
                    outcomes.add(runOne(seed, planters.get(seed), decay, threshold, ticks));
                }
                table.add(SweepStats.summarise(decay, threshold, outcomes));
            }
        }

        print(table);
        writeCsv(csv, table, ticks, firstSeed);
        System.out.println();
        System.out.println("Wrote " + csv.toAbsolutePath());
    }

    private static SweepStats.SeedOutcome runOne(long seed, int planter, double decay,
                                                 double tellThreshold, int ticks) {
        Params base = Params.defaults();
        Params params = new Params(tellThreshold, base.repeatWeight(), base.contradictionFactor(),
                decay, base.forgetThreshold(), base.mutationChance(), base.plantedConfidence());

        List<Input> inputs = List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, planter));
        Run run = Run.execute(seed, params, inputs, ticks);
        RumorStats stats = RumorStats.of(run.log());
        int family = stats.families().first();

        int daysWithBeliever = 0;
        for (RumorStats.DayStats day : stats.daily(family)) {
            if (day.believes() > 0) {
                daysWithBeliever++;
            }
        }
        return new SweepStats.SeedOutcome(seed, planter, stats.peakHeard(family),
                stats.peakBelieves(family), daysWithBeliever,
                stats.ticksUntilHalfBelieves(family).isPresent());
    }

    /**
     * The villager most likely to pass a rumor on. One tick is enough to create the
     * village and roll its traits. Ties go to the lower id.
     *
     * <p>The CLI does the same thing for its default planter; the two are deliberately
     * separate because neither module should depend on the other.
     */
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

    private static void print(List<SweepStats.Summary> table) {
        System.out.println("                      peak believes (of " + Simulation.VILLAGER_COUNT + ")");
        System.out.println("  decay  tell    mean   p10   p90   half-believes   days w/ believer   overshoot   peak heard");
        double lastDecay = Double.NaN;
        for (SweepStats.Summary row : table) {
            if (!Double.isNaN(lastDecay) && row.dailyDecay() != lastDecay) {
                System.out.println();
            }
            lastDecay = row.dailyDecay();
            System.out.printf("   %.2f  %.1f   %5.1f  %4d  %4d   %12s   %16.1f   %9s   %10.1f%n",
                    row.dailyDecay(), row.tellThreshold(), row.meanPeakBelieves(),
                    row.p10PeakBelieves(), row.p90PeakBelieves(),
                    percent(row.shareReachingHalf()), row.meanDaysWithBeliever(),
                    percent(row.shareOvershooting()), row.meanPeakHeard());
        }
    }

    private static String percent(double share) {
        return Math.round(share * 100) + "%";
    }

    private static void writeCsv(Path csv, List<SweepStats.Summary> table, int ticks, long firstSeed)
            throws IOException {
        if (csv.getParent() != null) {
            Files.createDirectories(csv.getParent());
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(csv))) {
            // Every row repeats the settings, so a row is readable without the command.
            out.println("dailyDecay,tellThreshold,seeds,firstSeed,ticks,meanPeakBelieves,"
                    + "p10PeakBelieves,p90PeakBelieves,shareReachingHalfBelieves,"
                    + "meanDaysWithBeliever,shareOvershooting,meanPeakHeard");
            for (SweepStats.Summary row : table) {
                out.printf("%.2f,%.2f,%d,%d,%d,%.3f,%d,%d,%.3f,%.3f,%.3f,%.3f%n",
                        row.dailyDecay(), row.tellThreshold(), row.seeds(), firstSeed, ticks,
                        row.meanPeakBelieves(), row.p10PeakBelieves(), row.p90PeakBelieves(),
                        row.shareReachingHalf(), row.meanDaysWithBeliever(),
                        row.shareOvershooting(), row.meanPeakHeard());
            }
        }
    }

    private static final String USAGE = """
            Sweeps simulation settings and reports how far rumors get.

              --seeds N        how many seeds per combination (default 50)
              --first-seed N   the first seed, so a sweep can be repeated exactly (default 1)
              --ticks N        ticks per run (default 200, which is 50 days)
              --decay LIST     comma-separated dailyDecay values (default 0.85,0.90,0.93,0.95,0.97)
              --tell LIST      comma-separated tellThreshold values (default 0.2,0.3,0.4)
              --csv PATH       where to write the table (default sweep.csv)
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
