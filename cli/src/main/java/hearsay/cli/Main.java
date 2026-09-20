package hearsay.cli;

import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.Input;
import hearsay.Narrator;
import hearsay.Params;
import hearsay.PlantRumor;
import hearsay.RumorStats;
import hearsay.Simulation;

import java.util.List;
import java.util.OptionalLong;

/**
 * Plants one rumor in villager 0 on day 1 and prints what the village does with it.
 *
 * <p>./gradlew :cli:run --args="&lt;seed&gt; &lt;ticks&gt; &lt;villager to plant in&gt;"
 */
public final class Main {

    private static final long DEFAULT_SEED = 42;
    private static final int DEFAULT_TICKS = 40; // ten days
    private static final int DEFAULT_PLANTED_IN = 0;
    private static final Claim DIAMONDS_SCARCE = new Claim("diamond", ClaimType.SCARCE);

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : DEFAULT_SEED;
        int ticks = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_TICKS;
        int plantedIn = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_PLANTED_IN;

        Params params = Params.defaults();
        List<Input> inputs = List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, plantedIn));

        Simulation sim = new Simulation(seed, params, inputs);
        sim.run(ticks);

        var planter = sim.state().villager(plantedIn);
        System.out.println("Hearsay: seed " + seed + ", " + ticks + " ticks");
        System.out.printf("Planted in %s (gossip %.2f, credulity %.2f)%n",
                planter.name(), planter.traits().gossip(), planter.traits().credulity());
        System.out.println();
        for (String line : Narrator.of(params).narrate(sim.log())) {
            System.out.println(line);
        }

        RumorStats stats = RumorStats.of(sim.log(), params);
        for (int family : stats.families()) {
            System.out.println();
            System.out.println("-- " + stats.claimOf(family).item() + " "
                    + stats.claimOf(family).type().name().toLowerCase() + " (rumor " + family + ") --");

            List<Integer> perDay = stats.believersPerDay(family);
            for (int day = 0; day < perDay.size(); day++) {
                System.out.printf("  day %-3d %-21s %d/%d%n",
                        day + 1, bar(perDay.get(day)), perDay.get(day), Simulation.VILLAGER_COUNT);
            }

            OptionalLong half = stats.ticksUntilHalfTheVillage(family);
            System.out.println("  peak believers:   " + stats.peakBelievers(family)
                    + "/" + Simulation.VILLAGER_COUNT);
            System.out.println("  half the village: "
                    + (half.isPresent() ? "tick " + half.getAsLong() : "never reached"));
            System.out.printf("  reproduction:     %.2f new believers per believer%n",
                    stats.reproductionNumber(family));
        }

        System.out.println();
        System.out.println(sim.state() + ", events=" + sim.log().size());
    }

    private static String bar(int believers) {
        return "#".repeat(believers) + ".".repeat(Simulation.VILLAGER_COUNT - believers);
    }
}
