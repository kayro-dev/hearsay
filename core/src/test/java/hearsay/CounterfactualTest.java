package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What the village would have done without the lie. */
class CounterfactualTest {

    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);
    private static final long SEED = 11;
    private static final int TICKS = 160; // 40 days

    private static PlantRumor lieAt(long tick) {
        int planter = Run.execute(SEED, Params.defaults(), List.of(), 1)
                .finalState().gossipiestVillager().id();
        return new PlantRumor(tick, DIAMONDS_SCARCE, 1, planter);
    }

    private static Run runWith(List<Input> inputs) {
        return Run.execute(SEED, Params.defaults(), inputs, TICKS);
    }

    @Test
    void removingAnInputThatWasNeverThereChangesNothing() {
        Run original = runWith(List.of(lieAt(1)));

        Run untouched = original.without(new PlantRumor(99, DIAMONDS_SCARCE, 3, 0));

        assertEquals(original.log(), untouched.log());
    }

    @Test
    void withoutTheLieNobodyPlantsOrTellsAnything() {
        Run without = runWith(List.of());

        for (Event event : without.log()) {
            assertFalse(event instanceof RumorPlanted, "nothing was planted");
            assertFalse(event instanceof RumorTold, "so there was nothing to pass on");
        }
    }

    @Test
    void removingTheLieLeavesEveryoneWalkingTheSameRoutes() {
        Run withLie = runWith(List.of(lieAt(1)));
        Run withoutLie = withLie.without(lieAt(1));

        assertEquals(physical(withoutLie.log()), physical(withLie.log()));
        assertNotEquals(withLie.log(), withoutLie.log(), "but the lie must have done something");
    }

    private static List<Event> physical(List<Event> log) {
        List<Event> found = new ArrayList<>();
        for (Event event : log) {
            if (event instanceof VillagerMoved || event instanceof VillagersMet) {
                found.add(event);
            }
        }
        return found;
    }

    @Test
    void theTimelinesDivergeAtTheTickTheLieWasTold() {
        long toldAt = 41; // the morning of day 11
        Run withLie = runWith(List.of(lieAt(toldAt)));
        Run withoutLie = runWith(List.of());

        Comparison comparison = Comparison.of(withLie.log(), withoutLie.log(), DIAMONDS_SCARCE);

        assertEquals(toldAt, comparison.divergenceTick().orElseThrow(),
                "anything earlier means the two runs differ for some reason other than the lie");
    }

    @Test
    void twoIdenticalTimelinesDoNotDivergeAtAll() {
        Run once = runWith(List.of());
        Run again = runWith(List.of());

        Comparison comparison = Comparison.of(once.log(), again.log(), DIAMONDS_SCARCE);

        assertTrue(comparison.identical());
        assertTrue(comparison.divergenceTick().isEmpty());
        assertEquals(0, comparison.extraCostOfADiamondEachMarketDay());
    }

    @Test
    void theComparisonReportsBothTimelinesDayByDay() {
        Comparison comparison = Comparison.of(
                runWith(List.of(lieAt(1))).log(), runWith(List.of()).log(), DIAMONDS_SCARCE);

        assertFalse(comparison.daily().isEmpty());
        for (Comparison.DayLine line : comparison.daily()) {
            assertTrue(line.believersWith() <= line.heardWith(), "day " + line.day());
            assertTrue(line.believersWithout() <= line.heardWithout(), "day " + line.day());
        }
        assertTrue(comparison.peakPriceWith() > comparison.peakPriceWithout(),
                "the lie should have moved the price");
        assertTrue(comparison.extraCostOfADiamondEachMarketDay() > 0,
                "and so cost a daily buyer something");
    }
}
