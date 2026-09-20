package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Many worlds, paired so that only the lie differs inside each one. */
class PairedWorldsTest {

    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);
    private static final long SEED = 11;
    private static final int TICKS = 160;
    private static final long TOLD_AT = 41; // day 11, so there is a shared history first
    private static final int PAIRS = 8;

    private static PlantRumor theLie() {
        int planter = Run.execute(SEED, Params.defaults(), List.of(), 1)
                .finalState().gossipiestVillager().id();
        return new PlantRumor(TOLD_AT, DIAMONDS_SCARCE, 1, planter);
    }

    private static PairedWorlds worlds() {
        return PairedWorlds.run(SEED, Params.defaults(), theLie(), TICKS, PAIRS);
    }

    @Test
    void insideAPairTheTwoWorldsAreIdenticalUntilTheLieIsTold() {
        for (PairedWorlds.Pair pair : worlds().pairs()) {
            Comparison comparison = pair.comparedOn(DIAMONDS_SCARCE);
            assertEquals(TOLD_AT, comparison.divergenceTick().orElseThrow(),
                    "pair " + pair.index() + " diverged before the lie, so the pairing is broken");
        }
    }

    @Test
    void everyWorldSharesTheHistoryFromBeforeTheLie() {
        PairedWorlds worlds = worlds();
        List<Event> shared = worlds.sharedHistory();

        assertFalse(shared.isEmpty(), "there should be a history to share");
        for (Event event : shared) {
            assertTrue(event.tick() < TOLD_AT, "the shared history stops before the lie");
        }
        for (PairedWorlds.Pair pair : worlds.pairs()) {
            assertEquals(shared, pair.withLie().subList(0, shared.size()));
            assertEquals(shared, pair.withoutLie().subList(0, shared.size()));
        }
    }

    @Test
    void insideAPairEveryoneWalksTheSameRoutesInBothWorlds() {
        // The whole point of pairing: the two worlds face the same future randomness, so
        // movement and meetings match all the way to the end, not merely up to the lie.
        // Without this, each pair would differ by the lie plus a pile of unrelated luck.
        for (PairedWorlds.Pair pair : worlds().pairs()) {
            assertEquals(physical(pair.withoutLie()), physical(pair.withLie()),
                    "pair " + pair.index() + " does not share its randomness");
        }
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
    void differentPairsTellDifferentStories() {
        List<List<Event>> continuations = new ArrayList<>();
        PairedWorlds worlds = worlds();
        int shared = worlds.sharedHistory().size();
        for (PairedWorlds.Pair pair : worlds.pairs()) {
            continuations.add(pair.withLie().subList(shared, pair.withLie().size()));
        }

        // If every world unfolded the same way, the branch seeds would not be doing
        // anything and the whole point of running many of them would be lost.
        for (int i = 1; i < continuations.size(); i++) {
            assertNotEquals(continuations.get(0), continuations.get(i),
                    "world " + i + " is a copy of world 0");
        }
    }

    @Test
    void theSameArgumentsProduceTheSameWorldsEveryTime() {
        PairedWorlds once = worlds();
        PairedWorlds again = worlds();

        assertEquals(once.pairs().size(), again.pairs().size());
        for (int i = 0; i < once.pairs().size(); i++) {
            assertEquals(once.pairs().get(i).branchSeed(), again.pairs().get(i).branchSeed());
            assertEquals(once.pairs().get(i).withLie(), again.pairs().get(i).withLie());
            assertEquals(once.pairs().get(i).withoutLie(), again.pairs().get(i).withoutLie());
        }
    }

    @Test
    void theLieCausesBubblesInSomeWorldsButNotNecessarilyAll() {
        PairedWorlds worlds = worlds();

        int caused = worlds.pairsWhereTheLieCausedABubble(DIAMONDS_SCARCE, 130, 110);
        int withLie = worlds.worldsThatBubbledWithTheLie(DIAMONDS_SCARCE, 130, 110);
        int withoutLie = worlds.worldsThatBubbledWithoutIt(DIAMONDS_SCARCE, 130, 110);

        assertTrue(caused <= withLie, "it cannot have caused more bubbles than there were");
        assertTrue(withLie >= withoutLie, "the lie should not make bubbles rarer");
        assertTrue(withLie > 0, "some world should have bubbled");
        assertTrue(worlds.meanPeakPriceDifference(DIAMONDS_SCARCE) > 0,
                "the lie should raise the peak price on average");
    }

    @Test
    void aLieToldAfterTheRunEndsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PairedWorlds.run(
                SEED, Params.defaults(), new PlantRumor(500, DIAMONDS_SCARCE, 1, 0), TICKS, 2));
    }
}
