package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The clock lives in the world, not in {@link Simulation}, so a forked world cannot lose
 * it. That only works while every tick writes down at least one thing: the next tick starts
 * from the world's tick, so a tick that records nothing would leave the clock where it was
 * and the tick after would silently repeat it.
 *
 * <p>Meetings are not what holds this up, and never were. A tick where nobody meets anybody
 * is ordinary — most nights are one, since nearly everyone is at home and home is twenty
 * separate houses. These tests pin that down so a future change to how meetings happen
 * cannot be blamed for, or quietly break, the clock.
 */
class ClockTest {

    private static final int TICKS = 200;

    private static List<Event> quietVillage() {
        return Run.execute(11, Params.defaults(), List.of(), TICKS).log();
    }

    private static TreeSet<Long> ticksWithMeetings(List<Event> log) {
        TreeSet<Long> ticks = new TreeSet<>();
        for (Event event : log) {
            if (event instanceof VillagersMet) {
                ticks.add(event.tick());
            }
        }
        return ticks;
    }

    @Test
    void ticksWhereNobodyMeetsAnybodyStillMoveTheClock() {
        List<Event> log = quietVillage();
        TreeSet<Long> withMeetings = ticksWithMeetings(log);

        List<Long> withoutMeetings = new ArrayList<>();
        for (long tick = 1; tick <= TICKS; tick++) {
            if (!withMeetings.contains(tick)) {
                withoutMeetings.add(tick);
            }
        }

        assertFalse(withoutMeetings.isEmpty(),
                "this run has a meeting on every tick, so it cannot check the quiet ones");
        for (long tick : withoutMeetings) {
            assertTrue(hasEventsOn(log, tick),
                    "tick " + tick + " had no meetings and recorded nothing at all");
        }
    }

    @Test
    void everyTickFromFirstToLastAppearsInTheLogExactlyOnce() {
        List<Event> log = quietVillage();

        // A missing tick means the clock stopped; a repeated one means it went backwards.
        TreeSet<Long> seen = new TreeSet<>();
        long previous = 0;
        for (Event event : log) {
            assertTrue(event.tick() >= previous, "the log went backwards at tick " + event.tick());
            previous = event.tick();
            seen.add(event.tick());
        }
        assertEquals(TICKS, seen.size(), "some tick is missing from the log");
        assertEquals(1L, seen.first());
        assertEquals((long) TICKS, seen.last());
    }

    @Test
    void theClockAdvancesByExactlyOneEveryStep() {
        Simulation sim = new Simulation(11, Params.defaults(), List.of());

        for (long expected = 1; expected <= 40; expected++) {
            sim.step();
            assertEquals(expected, sim.state().tick(),
                    "the world's clock is the only clock, and it must move once per step");
        }
    }

    @Test
    void aWorldResumedAfterAQuietTickCarriesOnFromTheRightPlace() {
        // Stop on a night, which is where the quiet ticks are, and carry on from there.
        Simulation sim = new Simulation(11, Params.defaults(), List.of());
        sim.run(48); // tick 48 is a night
        assertEquals(DayPart.NIGHT, DayPart.of(48));

        Simulation carried = Simulation.resume(
                Simulation.replay(sim.log()), 999, Params.defaults(), List.of());
        carried.run(1);

        assertEquals(49, carried.state().tick());
        assertEquals(49, carried.log().get(0).tick());
    }

    private static boolean hasEventsOn(List<Event> log, long tick) {
        for (Event event : log) {
            if (event.tick() == tick) {
                return true;
            }
        }
        return false;
    }
}
