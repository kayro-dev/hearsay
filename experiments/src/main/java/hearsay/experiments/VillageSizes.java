package hearsay.experiments;

import hearsay.Bubble;
import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.Input;
import hearsay.MarketStats;
import hearsay.Params;
import hearsay.PlantRumor;
import hearsay.Run;
import hearsay.Simulation;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Calibration across village sizes, on the simulated movement model and on a village
 * somebody actually played.
 *
 * <p>Two questions the earlier sweeps could not ask. Everything was tuned at twenty
 * villagers, so figures counted out of twenty were the only figures there were; and
 * everything was tuned on simulated movement, which mixes a village far more than real
 * villagers do. Both are reported here as shares, so a village of eight and a village of
 * thirty can be read side by side.
 *
 * <pre>
 * ./gradlew :experiments:sizes --args="--seeds 50 --sizes 8,12,20,30 --ticks 400 \
 *     --told-at 41 --window 30 --trace /path/to/session.hearsay --csv sizes.csv"
 * </pre>
 */
public final class VillageSizes {

    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    public static void main(String[] args) throws IOException {
        Map<String, String> options = Cli.parse(args);
        int seeds = Cli.intOption(options, "seeds", 50);
        long firstSeed = Cli.longOption(options, "first-seed", 1);
        int ticks = Cli.intOption(options, "ticks", 400);
        long toldAt = Cli.longOption(options, "told-at", 41);
        int window = Cli.intOption(options, "window", 30);
        List<Double> sizes = Cli.doubles(options.getOrDefault("sizes", "8,12,20,30"));
        Path csv = Path.of(options.getOrDefault("csv", "sizes.csv"));
        MeetingTrace trace = options.containsKey("trace")
                ? MeetingTrace.from(Path.of(options.get("trace"))) : null;

        System.out.printf(Locale.ROOT, "Village sizes: %d seeds (%d..%d), %d ticks, lie on tick %d, "
                        + "bubbles counted within %d days of it.%n",
                seeds, firstSeed, firstSeed + seeds - 1, ticks, toldAt, window);
        if (trace != null) {
            System.out.printf(Locale.ROOT, "Replaying %s: %d meetings recorded over %d ticks, naming %d "
                            + "villagers.%n",
                    trace.name(), trace.scheduleFor(Simulation.MOST_VILLAGERS, trace.ticks()).size(),
                    trace.ticks(), trace.villagersSeen());
        }
        System.out.println();
        System.out.println("                            with the lie        "
                + "   quiet village, per 100 days");
        System.out.println("  movement     size   bubbled   peak believers   "
                + "bubbles   belief onsets   peak$");

        List<Row> table = new ArrayList<>();
        for (double size : sizes) {
            table.add(measure("simulated", (int) size, (int) size, seeds, firstSeed, ticks,
                    toldAt, window, null));
        }
        if (trace != null) {
            // Only at the size the trace itself has. Running it at any other size either
            // leaves villagers with nobody to meet or throws away meetings, and neither
            // tells you anything about the village that was played.
            int size = trace.villagersSeen();
            table.add(measure("simulated", size, size, seeds, firstSeed, ticks, toldAt,
                    window, null));
            table.add(measure("played", size, size, seeds, firstSeed, ticks, toldAt,
                    window, trace));
        }
        table.forEach(VillageSizes::print);
        if (trace != null) {
            System.out.println();
            System.out.printf(Locale.ROOT, "  The played row rests on one recorded session of %d villagers, "
                    + "replayed under %d seeds.%n", trace.villagersSeen(), seeds);
            System.out.println("  Different seeds give those same bodies different "
                    + "personalities, but the meetings");
            System.out.println("  are the one village that was played, so this is a single "
                    + "village's evidence.");
        }
        writeCsv(csv, table, seeds, firstSeed, ticks, toldAt, window);
        System.out.println();
        System.out.println("Wrote " + csv.toAbsolutePath());
    }

    private static Row measure(String movement, int size, int active, int seeds, long firstSeed,
                               int ticks, long toldAt, int window, MeetingTrace trace) {
        int bubbled = 0;
        double totalBelieverShare = 0;
        double totalBubbleRate = 0;
        double totalOnsets = 0;
        double totalPeakPrice = 0;
        int runs = 0;

        for (long seed = firstSeed; seed < firstSeed + seeds; seed++) {
            Params params = trace == null
                    ? Params.defaults().withVillagers(size)
                    : MeetingTrace.paramsFor(Params.defaults(), size);

            List<Input> withLie = new ArrayList<>();
            List<Input> quiet = new ArrayList<>();
            if (trace != null) {
                withLie.addAll(trace.scheduleFor(size, ticks));
                quiet.addAll(trace.scheduleFor(size, ticks));
            }
            // Planted in villager 0, who exists at every size and meets people in the trace.
            withLie.add(new PlantRumor(toldAt, DIAMONDS_SCARCE, 1, 0));

            MarketStats told = MarketStats.of(Run.execute(seed, params, withLie, ticks).log(),
                    DIAMONDS_SCARCE);
            MarketStats untold = MarketStats.of(Run.execute(seed, params, quiet, ticks).log(),
                    DIAMONDS_SCARCE);

            if (told.bubbleWithin(toldAt, window).isPresent()) {
                bubbled++;
            }
            totalBelieverShare += told.peakBelieversFraction();
            totalBubbleRate += untold.bubblesPerHundredDays();
            totalOnsets += untold.beliefOnsetsPerHundredDays();
            totalPeakPrice += told.peakPrice();
            runs++;
        }
        return new Row(movement, size, active, bubbled / (double) runs,
                totalBelieverShare / runs, totalBubbleRate / runs, totalOnsets / runs,
                totalPeakPrice / runs);
    }

    private record Row(String movement, int size, int active, double bubbled,
                       double peakBelieverShare, double bubblesPerHundredDays,
                       double beliefOnsetsPerHundredDays, double peakPrice) {}

    private static void print(Row row) {
        System.out.printf(Locale.ROOT, "  %-11s %4d %9s %16s %9.2f %15.2f %7.1f%n",
                row.movement(), row.size(),
                percent(row.bubbled()), percent(row.peakBelieverShare()),
                row.bubblesPerHundredDays(), row.beliefOnsetsPerHundredDays(),
                row.peakPrice());
    }

    private static String percent(double share) {
        return Math.round(share * 100) + "%";
    }

    private static void writeCsv(Path csv, List<Row> table, int seeds, long firstSeed, int ticks,
                                 long toldAt, int window) throws IOException {
        if (csv.getParent() != null) {
            Files.createDirectories(csv.getParent());
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(csv))) {
            out.println("movement,villagers,activeVillagers,seeds,firstSeed,ticks,toldAt,"
                    + "windowDays,bubblePeakAbove,bubbleBackBelow,shareBubbled,"
                    + "meanPeakBelieverShare,quietBubblesPerHundredDays,"
                    + "quietBeliefOnsetsPerHundredDays,meanPeakPrice");
            for (Row row : table) {
                out.printf(Locale.ROOT, "%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.4f,%.4f,%.3f,%.3f,%.2f%n",
                        row.movement(), row.size(), row.active(), seeds, firstSeed, ticks,
                        toldAt, window, Bubble.PEAK_ABOVE, Bubble.BACK_BELOW,
                        row.bubbled(), row.peakBelieverShare(),
                        row.bubblesPerHundredDays(), row.beliefOnsetsPerHundredDays(),
                        row.peakPrice());
            }
        }
    }
}
