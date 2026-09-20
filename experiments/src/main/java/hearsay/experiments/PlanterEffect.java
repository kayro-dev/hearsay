package hearsay.experiments;

import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.Event;
import hearsay.Input;
import hearsay.MarketStats;
import hearsay.Params;
import hearsay.PlantRumor;
import hearsay.Run;
import hearsay.Seeds;
import hearsay.Simulation;
import hearsay.Villager;
import hearsay.WorldState;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Does it matter who you tell, or only when you tell them?
 *
 * <p>Telling two different villagers in one recorded village gave wildly different answers,
 * but that is two runs and proves nothing: the two also faced different luck. This takes
 * each villager in turn as the one told, and runs each of them through the same set of
 * futures, forked from one shared history at the moment before the lie.
 *
 * <p>Because world <em>w</em> uses the same branch seed whichever villager is told, the
 * comparison between villagers is paired: the luck cancels. That splits the variation in
 * two, which is the whole point.
 *
 * <ul>
 *   <li><b>within a planter</b> — the same villager told, across different futures. This is
 *       luck: who happened to walk past whom.</li>
 *   <li><b>between planters</b> — the spread of each villager's average across all futures.
 *       This is the villager: their personality and their place in the village.</li>
 * </ul>
 *
 * <pre>
 * ./gradlew :experiments:planters --args="--villages 5 --worlds 30 --ticks 300 \
 *     --told-at 41 --csv planters.csv"
 * </pre>
 */
public final class PlanterEffect {

    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    public static void main(String[] args) throws IOException {
        Map<String, String> options = Cli.parse(args);
        int villages = Cli.intOption(options, "villages", 5);
        long firstSeed = Cli.longOption(options, "first-seed", 1);
        int worlds = Cli.intOption(options, "worlds", 30);
        int ticks = Cli.intOption(options, "ticks", 300);
        long toldAt = Cli.longOption(options, "told-at", 41);
        int window = Cli.intOption(options, "window", 30);
        Params params = Params.defaults();
        Path csv = Path.of(options.getOrDefault("csv", "planters.csv"));

        System.out.printf("Planters: %d villages of %d, each villager told in turn, "
                        + "%d futures each.%n", villages, params.villagers(), worlds);
        System.out.printf("Forked from a shared history at tick %d; world w uses the same "
                + "branch seed whoever is told.%n", toldAt - 1);
        System.out.printf("That is %d runs.%n%n",
                villages * (params.villagers() * worlds + worlds));

        List<String> rows = new ArrayList<>();
        double totalWithin = 0;
        double totalBetween = 0;
        List<double[]> gossipAgainstOutcome = new ArrayList<>();

        System.out.println("  village   spread within a planter   spread between planters   "
                + "share explained by who");
        for (int village = 0; village < villages; village++) {
            long seed = firstSeed + village;

            // The history every future here shares, before the lie could change anything.
            Simulation parent = new Simulation(seed, params, List.of());
            parent.run((int) toldAt - 1);
            List<Event> history = List.copyOf(parent.log());
            WorldState villageState = Simulation.replay(history);

            double[][] outcomes = new double[params.villagers()][worlds];
            for (int planter = 0; planter < params.villagers(); planter++) {
                for (int world = 0; world < worlds; world++) {
                    outcomes[planter][world] = believerShare(history, Seeds.branch(seed, world),
                            params, planter, toldAt, ticks, window);
                }
            }

            double[] planterMeans = new double[params.villagers()];
            double within = 0;
            for (int planter = 0; planter < params.villagers(); planter++) {
                planterMeans[planter] = mean(outcomes[planter]);
                within += variance(outcomes[planter]);
                Villager villager = villageState.villager(planter);
                gossipAgainstOutcome.add(new double[] {villager.traits().gossip(),
                        planterMeans[planter]});
                rows.add(String.format("%d,%d,%s,%.3f,%.4f,%.4f", seed, planter,
                        villager.name(), villager.traits().gossip(), planterMeans[planter],
                        Math.sqrt(variance(outcomes[planter]))));
            }
            within /= params.villagers();
            double between = variance(planterMeans);
            totalWithin += within;
            totalBetween += between;

            System.out.printf("  %7d %24.3f %25.3f %23s%n", seed, Math.sqrt(within),
                    Math.sqrt(between), percent(between / (between + within)));
        }

        double within = totalWithin / villages;
        double between = totalBetween / villages;
        System.out.println();
        System.out.printf("  mean spread within a planter (luck):        %.3f%n", Math.sqrt(within));
        System.out.printf("  mean spread between planters (who):         %.3f%n", Math.sqrt(between));
        System.out.printf("  share of variation explained by who is told: %s%n",
                percent(between / (between + within)));
        System.out.printf("  gossip against a planter's average outcome:  r = %.2f (n = %d)%n",
                correlation(gossipAgainstOutcome), gossipAgainstOutcome.size());

        if (csv.getParent() != null) {
            Files.createDirectories(csv.getParent());
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(csv))) {
            out.println("villageSeed,planter,name,gossip,meanBelieverShare,sdAcrossFutures");
            rows.forEach(out::println);
        }
        System.out.println();
        System.out.println("Wrote " + csv.toAbsolutePath());
    }

    /** One future: the lie told to one villager, measured by how far belief got. */
    private static double believerShare(List<Event> history, long branchSeed, Params params,
                                        int planter, long toldAt, int ticks, int window) {
        List<Input> lie = List.of(new PlantRumor(toldAt, DIAMONDS_SCARCE, 1, planter));
        Simulation world = Simulation.resume(Simulation.replay(history), branchSeed, params, lie);
        world.run(ticks - (int) toldAt + 1);

        List<Event> whole = new ArrayList<>(history);
        whole.addAll(world.log());
        return MarketStats.of(whole, DIAMONDS_SCARCE).peakBelieversFraction();
    }

    private static double mean(double[] values) {
        double total = 0;
        for (double value : values) {
            total += value;
        }
        return total / values.length;
    }

    private static double variance(double[] values) {
        double mean = mean(values);
        double total = 0;
        for (double value : values) {
            total += (value - mean) * (value - mean);
        }
        return total / values.length;
    }

    /** Pearson correlation over (gossip, outcome) pairs. */
    private static double correlation(List<double[]> pairs) {
        int n = pairs.size();
        double sumX = 0;
        double sumY = 0;
        for (double[] pair : pairs) {
            sumX += pair[0];
            sumY += pair[1];
        }
        double meanX = sumX / n;
        double meanY = sumY / n;
        double top = 0;
        double leftBottom = 0;
        double rightBottom = 0;
        for (double[] pair : pairs) {
            double dx = pair[0] - meanX;
            double dy = pair[1] - meanY;
            top += dx * dy;
            leftBottom += dx * dx;
            rightBottom += dy * dy;
        }
        return top / Math.sqrt(leftBottom * rightBottom);
    }

    private static String percent(double share) {
        return Math.round(share * 100) + "%";
    }
}
