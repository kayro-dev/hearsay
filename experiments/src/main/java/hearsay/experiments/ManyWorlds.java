package hearsay.experiments;

import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.PairedWorlds;
import hearsay.Params;
import hearsay.PlantRumor;
import hearsay.Run;
import hearsay.Simulation;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs the paired-worlds comparison over many parent seeds and aggregates the answer.
 *
 * <p>One village's outcome could be luck, and so could one village's worth of worlds. This
 * asks the question over a whole population of villages: in what share of paired worlds did
 * the lie make the difference between a bubble and no bubble?
 *
 * <pre>
 * ./gradlew :experiments:worlds --args="--seeds 100 --first-seed 1001 --pairs 10 \
 *     --ticks 220 --told-at 41 --csv worlds.csv"
 * </pre>
 */
public final class ManyWorlds {

    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    public static void main(String[] args) throws IOException {
        Map<String, String> options = Cli.parse(args);
        int seeds = Cli.intOption(options, "seeds", 100);
        long firstSeed = Cli.longOption(options, "first-seed", 1001);
        int pairs = Cli.intOption(options, "pairs", 10);
        int ticks = Cli.intOption(options, "ticks", 220);
        long toldAt = Cli.longOption(options, "told-at", 41);
        Path csv = Path.of(options.getOrDefault("csv", "worlds.csv"));

        Params params = Params.defaults();

        System.out.printf(Locale.ROOT, "Paired worlds over %d villages (seeds %d..%d), %d pairs each, "
                        + "%d ticks, lie told on tick %d.%n",
                seeds, firstSeed, firstSeed + seeds - 1, pairs, ticks, toldAt);
        System.out.printf(Locale.ROOT, "That is %d worlds, run as %d pairs sharing a future within each pair.%n",
                seeds * pairs * 2, seeds * pairs);
        System.out.println();

        int totalPairs = 0;
        int causedByTheLie = 0;
        int bubbledWithTheLie = 0;
        int bubbledWithout = 0;
        double totalPeakDifference = 0;
        double totalExtraCost = 0;
        List<Double> perVillageShare = new ArrayList<>();
        List<String> rows = new ArrayList<>();

        for (long seed = firstSeed; seed < firstSeed + seeds; seed++) {
            int planter = Run.execute(seed, params, List.of(), 1)
                    .finalState().gossipiestVillager().id();
            PairedWorlds worlds = PairedWorlds.run(seed, params,
                    new PlantRumor(toldAt, DIAMONDS_SCARCE, 1, planter), ticks, pairs);

            int caused = worlds.pairsWhereTheLieCausedABubble(DIAMONDS_SCARCE);
            int withLie = worlds.worldsThatBubbledWithTheLie(DIAMONDS_SCARCE);
            int without = worlds.worldsThatBubbledWithoutIt(DIAMONDS_SCARCE);
            double meanPeak = worlds.meanPeakPriceDifference(DIAMONDS_SCARCE);
            double meanCost = worlds.meanExtraCost(DIAMONDS_SCARCE);

            totalPairs += pairs;
            causedByTheLie += caused;
            bubbledWithTheLie += withLie;
            bubbledWithout += without;
            totalPeakDifference += meanPeak * pairs;
            totalExtraCost += meanCost * pairs;
            perVillageShare.add(caused / (double) pairs);
            rows.add(String.format(Locale.ROOT, "%d,%d,%d,%d,%d,%d,%d,%d,%.2f,%.2f",
                    seed, planter, pairs, ticks, toldAt, caused, withLie, without,
                    meanPeak, meanCost));
        }

        Collections.sort(perVillageShare);
        System.out.printf(Locale.ROOT, "  pairs run:                       %d%n", totalPairs);
        System.out.printf(Locale.ROOT, "  bubbled with the lie:            %d (%.1f%%)%n",
                bubbledWithTheLie, 100.0 * bubbledWithTheLie / totalPairs);
        System.out.printf(Locale.ROOT, "  bubbled without it:              %d (%.1f%%)%n",
                bubbledWithout, 100.0 * bubbledWithout / totalPairs);
        System.out.printf(Locale.ROOT, "  the lie made the difference in:  %d (%.1f%%)%n",
                causedByTheLie, 100.0 * causedByTheLie / totalPairs);
        System.out.printf(Locale.ROOT, "  mean peak price effect:          %+.1f%n",
                totalPeakDifference / totalPairs);
        System.out.printf(Locale.ROOT, "  mean extra cost of a diamond a day: %+.1f%n",
                totalExtraCost / totalPairs);
        System.out.printf(Locale.ROOT, "  per-village share the lie caused: p10 %.0f%%, median %.0f%%, p90 %.0f%%%n",
                100 * percentile(perVillageShare, 0.10), 100 * percentile(perVillageShare, 0.50),
                100 * percentile(perVillageShare, 0.90));

        if (csv.getParent() != null) {
            Files.createDirectories(csv.getParent());
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(csv))) {
            out.println("seed,planter,pairs,ticks,toldAt,pairsLieCausedBubble,"
                    + "worldsBubbledWithLie,worldsBubbledWithout,meanPeakDifference,meanExtraCost");
            rows.forEach(out::println);
        }
        System.out.println();
        System.out.println("Wrote " + csv.toAbsolutePath());
    }

    private static double percentile(List<Double> sorted, double share) {
        int rank = (int) Math.ceil(share * sorted.size());
        return sorted.get(Math.min(sorted.size() - 1, Math.max(0, rank - 1)));
    }
}
