package hearsay.experiments;

import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.Event;
import hearsay.Input;
import hearsay.MarketPriceSet;
import hearsay.MarketStats;
import hearsay.Params;
import hearsay.PlantRumor;
import hearsay.PriceObserved;
import hearsay.RecipeFile;
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
 * How far the price must move before a villager reads anything into it.
 *
 * <p>The last market parameter never swept. It was set to a tenth in the week 5 design and
 * every sweep since has varied something around it. E17 found a played session whose market
 * settled at 107 against a threshold of 111 — one holder short of starting the feedback
 * loop, and stuck there for seven hundred ticks.
 *
 * <p>Lowering it is not free, and the cost is the reason it exists: a village that reads
 * meaning into a smaller move reads meaning into noise. So the spontaneous-panic rate is
 * reported beside the bubble rate, on villages nobody lied to, exactly as E5a measured it.
 *
 * <pre>
 * ./gradlew :experiments:threshold --args="--thresholds 0.03,0.05,0.07,0.10,0.15 \
 *     --traces <a.hearsay>,<b.hearsay> --seeds 40 --csv threshold.csv"
 * </pre>
 */
public final class ObservationThreshold {

    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    public static void main(String[] args) throws IOException {
        Map<String, String> options = Cli.parse(args);
        List<Double> thresholds = Cli.doubles(
                options.getOrDefault("thresholds", "0.03,0.05,0.07,0.10,0.15"));
        int seeds = Cli.intOption(options, "seeds", 40);
        int ticks = Cli.intOption(options, "ticks", 300);
        long toldAt = Cli.longOption(options, "told-at", 41);
        int window = Cli.intOption(options, "window", 30);
        Path csv = Path.of(options.getOrDefault("csv", "threshold.csv"));

        List<Path> traces = new ArrayList<>();
        if (options.containsKey("traces")) {
            for (String each : options.get("traces").split(",")) {
                traces.add(Path.of(each.trim()));
            }
        }

        List<String> rows = new ArrayList<>();

        if (!traces.isEmpty()) {
            System.out.println("Played sessions, replayed with the threshold changed and "
                    + "nothing else:");
            System.out.println("  threshold   session          peak$   observations   holders   "
                    + "believers   bubbles");
            for (Path trace : traces) {
                Run played = RecipeFile.read(trace);
                for (double threshold : thresholds) {
                    Run again = Run.execute(played.seed(),
                            played.params().withObservationThreshold(threshold),
                            played.inputs(), played.ticks());
                    MarketStats stats = MarketStats.of(again.log(), DIAMONDS_SCARCE);
                    long observed = again.log().stream()
                            .filter(e -> e instanceof PriceObserved).count();
                    String name = trace.getFileName().toString();
                    name = name.substring(Math.max(0, name.length() - 18));
                    System.out.printf("  %9.2f   %-15s %6d %14d %9d %11d %9d%n",
                            threshold, name, stats.peakPrice(), observed, stats.peakHolders(),
                            stats.peakBelievers(), stats.bubbles().size());
                    rows.add(String.format(Locale.ROOT, "played,%s,%.2f,%d,%d,%d,%d,%d",
                            name, threshold, stats.peakPrice(), observed, stats.peakHolders(),
                            stats.peakBelievers(), stats.bubbles().size()));
                }
                System.out.println();
            }
        }

        System.out.printf("Headless, %d villagers, %d seeds, a lie on tick %d, and the same "
                + "villages with nobody lying:%n", Params.defaults().villagers(), seeds, toldAt);
        System.out.println("  threshold   bubbled   peak$   believers   |   quiet: bubbles/100d   "
                + "belief onsets/100d");
        for (double threshold : thresholds) {
            Params params = Params.defaults().withObservationThreshold(threshold);
            int bubbled = 0;
            double peak = 0;
            double believers = 0;
            double quietBubbles = 0;
            double quietOnsets = 0;

            for (long seed = 1; seed <= seeds; seed++) {
                int planter = Run.execute(seed, params, List.of(), 1)
                        .finalState().gossipiestVillager().id();
                List<Input> lie = List.of(new PlantRumor(toldAt, DIAMONDS_SCARCE, 1, planter));
                MarketStats told = MarketStats.of(
                        Run.execute(seed, params, lie, ticks).log(), DIAMONDS_SCARCE);
                MarketStats quiet = MarketStats.of(
                        Run.execute(seed, params, List.of(), ticks).log(), DIAMONDS_SCARCE);

                if (told.bubbleWithin(toldAt, window).isPresent()) {
                    bubbled++;
                }
                peak += told.peakPrice();
                believers += told.peakBelieversFraction();
                quietBubbles += quiet.bubblesPerHundredDays();
                quietOnsets += quiet.beliefOnsetsPerHundredDays();
            }
            System.out.printf("  %9.2f %9s %7.1f %11s   |   %17.2f %19.2f%n",
                    threshold, percent(bubbled / (double) seeds), peak / seeds,
                    percent(believers / seeds), quietBubbles / seeds, quietOnsets / seeds);
            rows.add(String.format(Locale.ROOT, "headless,,%.2f,%.1f,%.4f,%.4f,%.3f,%.3f",
                    threshold, peak / seeds, bubbled / (double) seeds, believers / seeds,
                    quietBubbles / seeds, quietOnsets / seeds));
        }

        if (csv.getParent() != null) {
            Files.createDirectories(csv.getParent());
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(csv))) {
            out.println("source,session,observationThreshold,a,b,c,d,e");
            rows.forEach(out::println);
        }
        System.out.println();
        System.out.println("Wrote " + csv.toAbsolutePath());
    }

    private static String percent(double share) {
        return Math.round(share * 100) + "%";
    }
}
