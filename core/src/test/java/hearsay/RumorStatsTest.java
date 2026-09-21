package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The instrument: reading spread back out of a log. */
class RumorStatsTest {

    private static final Claim DIAMONDS_SCARCE = new Claim("diamond", ClaimType.SCARCE);

    private static Run aRun(int ticks) {
        // A perfectly mixed village, pinned rather than inherited: this is about what the reports say about a run,
        // not about how clustered a village is. E23 and E24 fitted the clustering to
        // recorded traces, and that fit should not decide whether a fixture spreads
        // far enough to have anything to measure.
        return Run.execute(42, Params.defaults().withMixing(1.0),
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
    void daysAtHalfPeakNeverExceedsDaysWithAnyBeliever() {
        RumorStats stats = RumorStats.of(aRun(200).log());
        int family = stats.families().first();

        int anyBeliever = 0;
        for (RumorStats.DayStats day : stats.daily(family)) {
            if (day.believes() > 0) {
                anyBeliever++;
            }
        }

        int atHalfPeak = stats.daysAtLeastHalfPeak(family);
        assertTrue(atHalfPeak > 0, "the rumor should have had a grip at some point");
        assertTrue(atHalfPeak <= anyBeliever,
                "a stricter measure cannot count more days: " + atHalfPeak + " vs " + anyBeliever);
    }

    @Test
    void everyDayCountedAtHalfPeakReallyHadHalfThePeakBelievers() {
        RumorStats stats = RumorStats.of(aRun(200).log());
        int family = stats.families().first();
        int peak = stats.peakBelieves(family);

        int counted = 0;
        for (RumorStats.DayStats day : stats.daily(family)) {
            if (day.believes() * 2 >= peak && peak > 0) {
                counted++;
            }
        }
        assertEquals(counted, stats.daysAtLeastHalfPeak(family));
    }

    @Test
    void theMedianBeliefSpellIsMeasuredOverSpellsThatFinished() {
        RumorStats stats = RumorStats.of(aRun(200).log());
        int family = stats.families().first();

        assertTrue(stats.completedBeliefSpells(family) > 0, "spells should have finished");
        assertTrue(stats.medianBeliefLifetimeDays(family).isPresent());
        double median = stats.medianBeliefLifetimeDays(family).getAsDouble();
        assertTrue(median > 0, "a spell that finished lasted some time");
        assertTrue(median <= stats.daily(family).size(), "a spell cannot outlast the run");
    }

    @Test
    void aRunNobodyEverBelievesHasNoMedianSpell() {
        // An impossible threshold: nobody ever crosses it, so no spell ever starts.
        RumorStats stats = RumorStats.of(aRun(200).log(), 1.01);
        int family = stats.families().first();

        assertEquals(0, stats.peakBelieves(family));
        assertEquals(0, stats.daysAtLeastHalfPeak(family));
        assertEquals(0, stats.completedBeliefSpells(family));
        assertTrue(stats.medianBeliefLifetimeDays(family).isEmpty());
    }

    @Test
    void theThresholdIsAReportingChoiceNotAChangeToTheRun() {
        List<Event> log = aRun(200).log();

        // Same log, different lens: the world it rebuilds is identical either way.
        assertEquals(Simulation.replay(log), Simulation.replay(log));
        assertEquals(RumorStats.of(log, 0.1).everHeard(0), RumorStats.of(log, 0.9).everHeard(0));
    }
}
