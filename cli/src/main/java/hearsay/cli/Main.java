package hearsay.cli;

import hearsay.Narrator;
import hearsay.Simulation;
import hearsay.Villager;

/**
 * Runs the village and prints what happened.
 *
 * <p>./gradlew :cli:run --args="&lt;seed&gt; &lt;ticks&gt;"
 */
public final class Main {

    private static final long DEFAULT_SEED = 42;
    private static final int DEFAULT_TICKS = 8; // two days

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : DEFAULT_SEED;
        int ticks = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_TICKS;

        Simulation sim = new Simulation(seed);
        sim.run(ticks);

        System.out.println("Hearsay: seed " + seed + ", " + ticks + " ticks");
        System.out.println();
        for (String line : new Narrator().narrate(sim.log())) {
            System.out.println(line);
        }

        System.out.println();
        System.out.println("-- where everyone ended up --");
        for (Villager villager : sim.state().villagers().values()) {
            System.out.println("  " + villager);
        }
        System.out.println();
        System.out.println(sim.state() + ", events=" + sim.log().size());
    }
}
