package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Guards the property that makes forking safe: {@link Simulation} keeps no state of its own
 * that a decision reads.
 *
 * <p>Everything a decision depends on lives in {@link WorldState}, which is rebuilt from the
 * log, so a forked world is a plain copy and cannot silently lose anything. The danger is
 * quiet: a field added here for convenience would be missing in a forked or resumed world,
 * and nothing would fail loudly — the counterfactual would simply be measuring something
 * slightly different from the run it claims to compare against.
 *
 * <p>These tests exist so that the rule is enforced rather than remembered. Adding a field
 * to Simulation makes them fail, which is the point: the failure asks whether the new field
 * belongs in WorldState instead.
 */
class SimulationStateTest {

    /**
     * The recipe ({@code seed}, {@code params}, {@code inputs}, {@code scheduled}), the
     * generators, the world, and the log being written. Nothing here is read to decide
     * anything except the world.
     */
    private static final Set<String> ALLOWED = Set.of(
            "seed", "params", "movement", "gossip", "mutation", "market",
            "inputs", "scheduled", "state", "log");

    private static Set<String> instanceFieldsOf(Class<?> type) {
        Set<String> names = new TreeSet<>();
        for (Field field : type.getDeclaredFields()) {
            if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers())) {
                names.add(field.getName());
            }
        }
        return names;
    }

    @Test
    void theSimulationHoldsNothingBeyondTheRecipeTheStreamsTheWorldAndTheLog() {
        assertEquals(new TreeSet<>(ALLOWED), instanceFieldsOf(Simulation.class),
                "Simulation gained or lost a field. If a decision reads it, it belongs in "
                        + "WorldState, or a forked world will not have it.");
    }

    @Test
    void everyFieldTheSimulationHoldsIsFinal() {
        for (Field field : Simulation.class.getDeclaredFields()) {
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            assertTrue(Modifier.isFinal(field.getModifiers()),
                    "Simulation." + field.getName() + " can be reassigned, so it is state "
                            + "this class carries between ticks. Decision state belongs in "
                            + "WorldState.");
        }
    }

    @Test
    void theTickComesFromTheWorldRatherThanACounterOfItsOwn() {
        Simulation sim = new Simulation(42, Params.defaults(), java.util.List.of());
        sim.run(7);

        // If the simulation counted ticks itself, a resumed world would restart at zero.
        Simulation carried = Simulation.resume(
                Simulation.replay(sim.log()), 99, Params.defaults(), java.util.List.of());
        carried.run(1);

        assertEquals(8, carried.state().tick());
    }
}
