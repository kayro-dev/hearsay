package hearsay.experiments;

import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.Input;
import hearsay.MarketStats;
import hearsay.Params;
import hearsay.Run;
import hearsay.Simulation;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tunes the market wobble on its own, before anything else is decided.
 *
 * <p>A village nobody has lied to should panic occasionally but not often. If it never
 * can, the claim that rumors cause bubbles is untestable, because the alternative has been
 * ruled out by construction rather than by measurement. Every run here is a quiet village:
 * no rumor is planted, so anything that happens came out of the noise.
 *
 * <pre>
 * ./gradlew :experiments:noise --args="--seeds 200 --ticks 200 \
 *     --noise 0.03,0.04,0.05,0.06,0.08 --decay 0.80,0.85,0.90,0.95 --csv noise.csv"
 * </pre>
 */
public final class Noise {

    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    public static void main(String[] args) throws IOException {
        Map<String, String> options = Cli.parse(args);
        if (options.containsKey("help")) {
            System.out.println(USAGE);
            return;
        }

        int seeds = Cli.intOption(options, "seeds", 200);
        long firstSeed = Cli.longOption(options, "first-seed", 1);
        int ticks = Cli.intOption(options, "ticks", 200);
        List<Double> noises = Cli.doubles(options.getOrDefault("noise", "0.03,0.04,0.05,0.06,0.08"));
        List<Double> decays = Cli.doubles(options.getOrDefault("decay", "0.80,0.85,0.90,0.95"));
        Path csv = Path.of(options.getOrDefault("csv", "noise.csv"));

        System.out.printf("Noise: %d quiet villages (seeds %d..%d), %d ticks, nothing planted%n",
                seeds, firstSeed, firstSeed + seeds - 1, ticks);
        System.out.println("Target: somebody comes to believe in roughly 2-8% of seeds.");
        System.out.println();
        System.out.println("  noise   decay   any holder   any believer   burst   peak$   max$");

        List<String> rows = new ArrayList<>();
        double lastNoise = Double.NaN;
        for (double noise : noises) {
            if (!Double.isNaN(lastNoise)) {
                System.out.println();
            }
            lastNoise = noise;
            for (double decay : decays) {
                Params params = Params.defaults().withMarketNoise(noise).withNoiseDecay(decay);

                int anyHolder = 0;
                int anyBeliever = 0;
                int burst = 0;
                long totalPeak = 0;
                int highest = 0;
                for (long seed = firstSeed; seed < firstSeed + seeds; seed++) {
                    MarketStats stats = MarketStats.of(
                            Run.execute(seed, params, List.<Input>of(), ticks).log(), DIAMONDS_SCARCE);
                    if (stats.peakHolders() > 0) {
                        anyHolder++;
                    }
                    if (stats.peakBelievers() > 0) {
                        anyBeliever++;
                    }
                    if (stats.bubble().isPresent()) {
                        burst++;
                    }
                    totalPeak += stats.peakPrice();
                    highest = Math.max(highest, stats.peakPrice());
                }
                System.out.printf("  %.3f   %.2f   %10s   %12s   %5s   %5.1f   %4d%n",
                        noise, decay, percent(anyHolder, seeds), percent(anyBeliever, seeds),
                        percent(burst, seeds), totalPeak / (double) seeds, highest);
                rows.add(String.format("%.3f,%.2f,%d,%d,%d,%.4f,%.4f,%.4f,%.2f,%d",
                        noise, decay, seeds, firstSeed, ticks,
                        anyHolder / (double) seeds, anyBeliever / (double) seeds,
                        burst / (double) seeds, totalPeak / (double) seeds, highest));
            }
        }

        if (csv.getParent() != null) {
            Files.createDirectories(csv.getParent());
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(csv))) {
            out.println("marketNoise,noiseDecay,seeds,firstSeed,ticks,shareAnyHolder,"
                    + "shareAnyBeliever,shareBurst,meanPeakPrice,highestPrice");
            rows.forEach(out::println);
        }
        System.out.println();
        System.out.println("Wrote " + csv.toAbsolutePath());
    }

    private static String percent(int count, int of) {
        return String.format("%.1f%%", 100.0 * count / of);
    }

    private static final String USAGE = """
            Runs quiet villages across a grid of noise settings and reports how often one panics.

              --seeds N       quiet villages per combination (default 200)
              --first-seed N  the first seed (default 1)
              --ticks N       ticks per run (default 200)
              --noise LIST    comma-separated marketNoise values
              --decay LIST    comma-separated noiseDecay values
              --csv PATH      where to write the table (default noise.csv)
            """;
}
