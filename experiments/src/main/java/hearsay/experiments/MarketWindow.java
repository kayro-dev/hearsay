package hearsay.experiments;

import hearsay.Bubble;
import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.Event;
import hearsay.Input;
import hearsay.MarketPriceSet;
import hearsay.MarketStats;
import hearsay.Params;
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
 * How long the market should remember its traders, swept against real villages rather than
 * chosen.
 *
 * <p>Where a villager stands is learned in glimpses, so a market read from a single instant
 * misses most of who is standing in it: a village of thirty-one opened its market on 1% of
 * ticks for that reason. A window gives a villager who was at a stall a moment ago a little
 * grace, which turns a snapshot into a sample.
 *
 * <p>Run against a saved session, and against the headless model for comparison, since
 * lengthening the window changes both.
 *
 * <pre>
 * ./gradlew :experiments:window --args="--trace <session> --windows 0,2,4,8,16,32 \
 *     --csv window.csv"
 * </pre>
 */
public final class MarketWindow {

    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    public static void main(String[] args) throws IOException {
        Map<String, String> options = Cli.parse(args);
        List<Double> windows = Cli.doubles(options.getOrDefault("windows", "0,2,4,8,16,32"));
        int seeds = Cli.intOption(options, "seeds", 30);
        Path csv = Path.of(options.getOrDefault("csv", "window.csv"));
        Path trace = options.containsKey("trace") ? Path.of(options.get("trace")) : null;

        List<String> rows = new ArrayList<>();

        if (trace != null) {
            Run played = RecipeFile.read(trace);
            System.out.printf(Locale.ROOT, "Played: %s, %d villagers, %d ticks, quorum %d%n",
                    trace.getFileName(), played.params().villagers(), played.ticks(),
                    played.params().marketQuorum());
            System.out.println("  window   ticks priced   peak$   peak holders   peak believers   bubbles");
            for (double window : windows) {
                Params params = played.params().withMarketWindowTicks((int) window);
                Run again = Run.execute(played.seed(), params, played.inputs(), played.ticks());
                MarketStats stats = MarketStats.of(again.log(), DIAMONDS_SCARCE);
                long priced = again.log().stream().filter(e -> e instanceof MarketPriceSet m && m.item().equals(Simulation.DIAMOND)).count();

                System.out.printf(Locale.ROOT, "  %6.0f %13s %7d %14d %16d %9d%n", window,
                        percent(priced / (double) played.ticks()), stats.peakPrice(),
                        stats.peakHolders(), stats.peakBelievers(), stats.bubbles().size());
                rows.add(String.format(Locale.ROOT, "played,%.0f,%.4f,%d,%d,%d,%d", window,
                        priced / (double) played.ticks(), stats.peakPrice(),
                        stats.peakHolders(), stats.peakBelievers(), stats.bubbles().size()));
            }
            System.out.println();
        }

        System.out.printf(Locale.ROOT, "Headless model, %d villagers, %d seeds, a lie on tick 41:%n",
                Params.defaults().villagers(), seeds);
        System.out.println("  window   ticks priced   peak$   peak believers   bubbled within 30d");
        for (double window : windows) {
            Params params = Params.defaults().withMarketWindowTicks((int) window);
            double priced = 0;
            double peak = 0;
            double believers = 0;
            int bubbled = 0;
            for (long seed = 1; seed <= seeds; seed++) {
                List<Input> lie = List.of(new hearsay.PlantRumor(41, DIAMONDS_SCARCE, 1,
                        Run.execute(seed, params, List.of(), 1).finalState()
                                .gossipiestVillager().id()));
                Run run = Run.execute(seed, params, lie, 300);
                MarketStats stats = MarketStats.of(run.log(), DIAMONDS_SCARCE);
                priced += run.log().stream().filter(e -> e instanceof MarketPriceSet m && m.item().equals(Simulation.DIAMOND)).count() / 300.0;
                peak += stats.peakPrice();
                believers += stats.peakBelieversFraction();
                if (stats.bubbleWithin(41, 30).isPresent()) {
                    bubbled++;
                }
            }
            System.out.printf(Locale.ROOT, "  %6.0f %13s %7.1f %16s %20s%n", window,
                    percent(priced / seeds), peak / seeds, percent(believers / seeds),
                    percent(bubbled / (double) seeds));
            rows.add(String.format(Locale.ROOT, "headless,%.0f,%.4f,%.2f,%.4f,%.4f", window,
                    priced / seeds, peak / seeds, believers / seeds, bubbled / (double) seeds));
        }

        if (csv.getParent() != null) {
            Files.createDirectories(csv.getParent());
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(csv))) {
            out.println("source,windowTicks,sharePriced,peakPrice,peakHoldersOrBelieverShare,"
                    + "peakBelieversOrBubbled,bubbles");
            rows.forEach(out::println);
        }
        System.out.println();
        System.out.println("A bubble is above " + Bubble.PEAK_ABOVE + " and back under "
                + Bubble.BACK_BELOW + ".");
        System.out.println("Wrote " + csv.toAbsolutePath());
    }

    private static String percent(double share) {
        return Math.round(share * 100) + "%";
    }
}
