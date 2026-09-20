package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The instrument: reading spread back out of a log. */
class RumorStatsTest {

    private static final Claim DIAMONDS_SCARCE = new Claim("diamond", ClaimType.SCARCE);

    private static Run aRun(int ticks) {
        return Run.execute(42, Params.defaults(),
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, 10)), ticks);
    }

    @Test
    void believingIsAlwaysASubsetOfHavingHeard() {
        RumorStats stats = RumorStats.of(aRun(200).log());
        int family = stats.families().first();

        for (RumorStats.DayStats day : stats.daily(family)) {
            assertTrue(day.believes() <= day.heard(),
                    "day " + day.day() + ": more believers than listeners");
            assertTrue(day.heard() <= Simulation.VILLAGER_COUNT);
        }
    }

    @Test
    void theSeverityBreakdownAccountsForEveryoneWhoHeard() {
        RumorStats stats = RumorStats.of(aRun(200).log());
        int family = stats.families().first();

        for (RumorStats.DayStats day : stats.daily(family)) {
            int counted = day.bySeverity().values().stream().mapToInt(Integer::intValue).sum();
            assertEquals(day.heard(), counted, "day " + day.day() + " severity counts do not add up");
        }
    }

    @Test
    void everHeardNeverFallsAndIsAtLeastThePeak() {
        RumorStats stats = RumorStats.of(aRun(200).log());
        int family = stats.families().first();

        assertTrue(stats.everHeard(family) >= stats.peakHeard(family),
                "cumulative reach cannot be below the highest single day");
        assertTrue(stats.peakHeard(family) >= stats.peakBelieves(family));
    }

    @Test
    void aHarsherThresholdNeverCountsMoreBelievers() {
        List<Event> log = aRun(200).log();
        RumorStats lenient = RumorStats.of(log, 0.1);
        RumorStats strict = RumorStats.of(log, 0.9);
        int family = lenient.families().first();

        assertEquals(lenient.peakHeard(family), strict.peakHeard(family),
                "the threshold must not change who heard it");
        assertTrue(strict.peakBelieves(family) <= lenient.peakBelieves(family));
    }

    @Test
    void theThresholdIsAReportingChoiceNotAChangeToTheRun() {
        List<Event> log = aRun(200).log();

        // Same log, different lens: the world it rebuilds is identical either way.
        assertEquals(Simulation.replay(log), Simulation.replay(log));
        assertEquals(RumorStats.of(log, 0.1).everHeard(0), RumorStats.of(log, 0.9).everHeard(0));
    }
}
