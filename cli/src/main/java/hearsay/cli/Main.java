package hearsay.cli;

import hearsay.Claim;
import hearsay.Comparison;
import hearsay.PairedWorlds;
import hearsay.ClaimType;
import hearsay.Input;
import hearsay.Narrator;
import hearsay.Params;
import hearsay.PlantRumor;
import hearsay.RumorStats;
import hearsay.Run;
import hearsay.Simulation;
import hearsay.Villager;
import hearsay.WorldState;

import java.util.List;
import java.util.OptionalLong;

/**
 * Plants one rumor on day 1 and prints what the village does with it. By default it
 * picks the villager most likely to pass it on.
 *
 * <p>./gradlew :cli:run --args="&lt;seed&gt; &lt;ticks&gt; &lt;villager to plant in&gt;"
 */
public final class Main {

    private static final long DEFAULT_SEED = 42;
    private static final int DEFAULT_TICKS = 40; // ten days
    private static final Claim DIAMONDS_SCARCE = new Claim("diamond", ClaimType.SCARCE);

    public static void main(String[] args) {
        String command = args.length > 0 && !args[0].startsWith("--") ? args[0] : "demo";
        String[] rest = command.equals("demo") ? args
                : java.util.Arrays.copyOfRange(args, 1, args.length);
        switch (command) {
            case "counterfactual" -> Counterfactual.print(rest);
            case "worlds" -> Worlds.print(rest);
            case "demo" -> demo(rest);
            default -> {
                System.out.println("Unknown command: " + command);
                System.out.println(USAGE);
            }
        }
    }

    private static final String USAGE = """
            ./gradlew :cli:run --args="<command> [options]"

              demo [seed] [ticks] [planter]   narrate one village
              counterfactual --seed N         one village, with and without the lie
              counterfactual --file PATH      a session you played, with and without it
              worlds --seed N --pairs M       many paired worlds, with and without the lie
            """;

    private static void demo(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : DEFAULT_SEED;
        int ticks = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_TICKS;
        Params params = Params.defaults();
        int plantedIn = args.length > 2 ? Integer.parseInt(args[2]) : gossipiestVillager(seed, params);
        List<Input> inputs = List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, plantedIn));

        Run run = Run.execute(seed, params, inputs, ticks);

        var planter = run.finalState().villager(plantedIn);
        System.out.println("Hearsay: seed " + seed + ", " + ticks + " ticks");
        System.out.printf("Planted in %s (gossip %.2f, credulity %.2f)%n",
                planter.name(), planter.traits().gossip(), planter.traits().credulity());
        System.out.println();
        for (String line : Narrator.of().narrate(run.log())) {
            System.out.println(line);
        }

        RumorStats stats = RumorStats.of(run.log());
        for (int family : stats.families()) {
            System.out.println();
            System.out.println("-- " + stats.claimOf(family).item() + " "
                    + stats.claimOf(family).type().name().toLowerCase() + " (rumor " + family + ") --");
            System.out.println("     # believes (>= " + (int) (stats.believeThreshold() * 100)
                    + "%)   + heard but unconvinced       severity");

            for (RumorStats.DayStats day : stats.daily(family)) {
                System.out.printf("  day %-3d %-21s %2d heard / %2d believe   %s%n",
                        day.day(), bar(day), day.heard(), day.believes(), severities(day));
            }

            System.out.println("  ever heard:       " + stats.everHeard(family)
                    + "/" + Simulation.VILLAGER_COUNT);
            System.out.println("  peak heard:       " + stats.peakHeard(family)
                    + "/" + Simulation.VILLAGER_COUNT);
            System.out.println("  peak believes:    " + stats.peakBelieves(family)
                    + "/" + Simulation.VILLAGER_COUNT);
            System.out.println("  half heard:       " + describe(stats.ticksUntilHalfHeard(family)));
            System.out.println("  half believes:    " + describe(stats.ticksUntilHalfBelieves(family)));
            System.out.printf("  reproduction:     %.2f new people reached per person reached%n",
                    stats.reproductionNumber(family));
        }

        System.out.println();
        System.out.println(run.finalState() + ", events=" + run.log().size()
                + ", inputs=" + run.inputs().size());
    }

    /**
     * The villager most likely to actually pass a rumor on. Found by running one tick with
     * no inputs, which is enough to create the village and roll its traits.
     *
     * <p>This costs nothing in fidelity: traits are drawn from the movement stream, and
     * inputs never touch that stream, so the village this probe sees is the same village
     * the real run gets. A planter picked at random is often a dud - three of twenty
     * villagers talk so rarely that the rumor dies with them.
     */
    private static int gossipiestVillager(long seed, Params params) {
        return Run.execute(seed, params, List.of(), 1).finalState().gossipiestVillager().id();
    }

    private static String bar(RumorStats.DayStats day) {
        int unconvinced = day.heard() - day.believes();
        return "#".repeat(day.believes()) + "+".repeat(unconvinced)
                + ".".repeat(Simulation.VILLAGER_COUNT - day.heard());
    }

    /** e.g. "3x1 1x2" - three holding the mild version, one holding the worse one. */
    private static String severities(RumorStats.DayStats day) {
        if (day.bySeverity().isEmpty()) {
            return "-";
        }
        StringBuilder text = new StringBuilder();
        day.bySeverity().forEach((severity, count) ->
                text.append(count).append("x").append(severity).append(" "));
        return text.toString().trim();
    }

    private static String describe(OptionalLong tick) {
        return tick.isPresent() ? "tick " + tick.getAsLong() : "never reached";
    }
}
