package hearsay;

import java.util.List;

/**
 * One run of the village, and everything needed to produce it again.
 *
 * <p>The recipe is the seed, the {@link Params}, the {@link Input}s and how many ticks
 * were run. Keeping those together with the log they produced is the point of this type:
 * a log on its own cannot be re-derived, and a recipe stored somewhere apart from its log
 * eventually drifts away from it.
 *
 * <p>{@link #rerun()} rebuilds the log from the recipe and must produce exactly the same
 * events. That is a stronger claim than replay: replay re-applies events that were already
 * decided, while a rerun decides them all again from the seed.
 */
public record Run(long seed, Params params, List<Input> inputs, int ticks, List<Event> log) {

    public Run {
        inputs = List.copyOf(inputs);
        log = List.copyOf(log);
    }

    /** Runs the recipe and keeps the log it produced. */
    public static Run execute(long seed, Params params, List<Input> inputs, int ticks) {
        Simulation sim = new Simulation(seed, params, inputs);
        sim.run(ticks);
        return sim.toRun();
    }

    /** Decides the whole run again from the recipe alone. */
    public Run rerun() {
        return execute(seed, params, inputs, ticks);
    }

    /** Rebuilds the world by re-applying the log, without deciding anything again. */
    public WorldState finalState() {
        return Simulation.replay(log);
    }

    /**
     * The same recipe with one input left out: the starting point for a counterfactual.
     * The log is not carried over, because with a different input it no longer applies.
     */
    public Run without(Input input) {
        List<Input> remaining = new java.util.ArrayList<>(inputs);
        remaining.remove(input);
        return execute(seed, params, remaining, ticks);
    }

    /** Just the events that came from outside: what a counterfactual varies. */
    public List<Event> inputEvents() {
        List<Event> found = new java.util.ArrayList<>();
        for (Event event : log) {
            if (event.isInput()) {
                found.add(event);
            }
        }
        return found;
    }
}
