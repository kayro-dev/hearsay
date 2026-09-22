package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A world must be able to carry on from its own written-down state.
 *
 * <p>Replay only re-applies decisions already made, so it never asks whether the state is
 * complete. Forking does: a continuation has to decide what happens next, and anything a
 * decision depends on that lives outside {@link WorldState} is silently lost when a world
 * is rebuilt from its log. These tests continue the same village twice, once from the live
 * state and once from a replayed one, and demand the two carry on identically.
 */
class ResumeTest {

    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);
    private static final long BRANCH_SEED = 777;

    private static List<Input> aRumorAt(long tick, long seed) {
        int planter = Run.execute(seed, Params.defaults(), List.of(), 1)
                .finalState().gossipiestVillager().id();
        return List.of(new PlantRumor(tick, DIAMONDS_SCARCE, 1, planter));
    }

    @Test
    void aReplayedWorldCarriesOnExactlyAsTheLiveOneWould() {
        Params params = Params.defaults();
        Simulation live = new Simulation(42, params, aRumorAt(1, 42));
        live.run(60);
        List<Event> prefix = List.copyOf(live.log());

        // Forking carries everything the running simulation knows; resuming rebuilds from
        // the log alone. Anything a decision needs that the log does not record shows up
        // here as a difference between the two.
        Simulation fromLive = live.fork(BRANCH_SEED, List.of());
        fromLive.run(40);

        Simulation fromReplay = Simulation.resume(
                Simulation.replay(prefix), BRANCH_SEED, params, List.of());
        fromReplay.run(40);

        assertFalse(fromLive.log().isEmpty(), "the continuation should have done something");
        assertEquals(fromLive.log(), fromReplay.log(),
                "a world rebuilt from its log carries on differently, so something a "
                        + "decision depends on is not in WorldState");
        assertEquals(fromLive.state(), fromReplay.state());
    }

    @Test
    void aResumedWorldPicksUpTheTickItLeftOff() {
        Simulation live = new Simulation(42, Params.defaults(), List.of());
        live.run(37);

        Simulation carried = Simulation.resume(
                Simulation.replay(live.log()), BRANCH_SEED, Params.defaults(), List.of());
        carried.run(1);

        assertEquals(38, carried.state().tick());
        assertEquals(38, carried.log().get(0).tick(), "the first new event belongs to tick 38");
    }

    @Test
    void aWorldResumedMidBubbleKeepsWhatEveryoneAlreadyBelieved() {
        Params params = Params.defaults();
        Simulation live = new Simulation(11, params, aRumorAt(1, 11));
        live.run(80);
        WorldState before = Simulation.replay(live.log());

        Simulation carried = Simulation.resume(before, BRANCH_SEED, params, List.of());

        assertEquals(live.state().villagers(), carried.state().villagers());
        assertEquals(live.state().rumors(), carried.state().rumors());
        assertEquals(live.state().marketPrice(Good.DIAMOND), carried.state().marketPrice(Good.DIAMOND));
    }
}
