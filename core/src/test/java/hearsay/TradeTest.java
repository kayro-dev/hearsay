package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Selling diamonds to a village that believes there are none. */
class TradeTest {

    private static final Claim SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);
    private static final Claim ABUNDANT = new Claim(Simulation.DIAMOND, ClaimType.ABUNDANT);

    private static NavigableSet<Integer> watching(int... ids) {
        NavigableSet<Integer> set = new TreeSet<>();
        for (int id : ids) {
            set.add(id);
        }
        return set;
    }

    /**
     * Stops before nightfall on purpose. A single diamond convinces somebody by about half
     * a percent, which is less than {@code forgetThreshold}, so the day's fading drops it —
     * correctly, since a belief that faint is not one. These tests are about what the sale
     * did, not about what survives the night.
     */
    private static Run sellInto(Params params, int count, int... witnesses) {
        return Run.execute(42, params,
                List.of(new PlayerTraded(2, witnesses[0], count, count * 8, watching(witnesses))),
                3);
    }

    @Test
    void sellingDiamondsIsEvidenceThereAreDiamonds() {
        WorldState after = sellInto(Params.defaults(), 16, 0, 1, 2).finalState();

        for (int id : new int[] {0, 1, 2}) {
            Belief plenty = after.villager(id).belief(ABUNDANT);
            assertNotNull(plenty, "villager " + id + " watched and learned nothing");
            assertTrue(plenty.confidence() > 0);
        }
    }

    @Test
    void whoeverTookTheGoodsIsConvincedMostByThem() {
        // The same villager in both roles, because villagers differ in how credulous they
        // are and comparing two of them would pass on that difference alone — as it did
        // until a mutation that ignored the distinction survived this test.
        Params params = Params.defaults();
        double asTrader = sellInto(params, 16, 1).finalState()
                .villager(1).belief(ABUNDANT).confidence();
        double asOnlooker = Run.execute(42, params,
                List.of(new PlayerTraded(2, 0, 16, 128, watching(0, 1))), 3)
                .finalState().villager(1).belief(ABUNDANT).confidence();

        assertTrue(asTrader > asOnlooker,
                "villager 1 should be surer having taken the diamonds than having watched "
                        + "somebody else take them: " + asTrader + " against " + asOnlooker);
    }

    @Test
    void oneDiamondIsACuriosityAndAStackIsAGlut() {
        double fromOne = sellInto(Params.defaults(), 1, 0).finalState()
                .villager(0).belief(ABUNDANT).confidence();
        double fromMany = sellInto(Params.defaults(), 32, 0).finalState()
                .villager(0).belief(ABUNDANT).confidence();

        assertTrue(fromMany > fromOne, "a stack should say more than a single diamond");
    }

    @Test
    void nobodyWhoWasNotThereLearnsAnything() {
        WorldState after = sellInto(Params.defaults(), 16, 0, 1).finalState();

        assertNull(after.villager(5).belief(ABUNDANT),
                "villager 5 was not standing there and cannot have seen it");
    }

    @Test
    void seeingIsNeverARepeatOfHavingBeenTold() {
        // The whole point of the mechanism. A villager talked into a shortage has heard it
        // from people; watching diamonds arrive is not one of those people saying it again,
        // so it cannot be discounted as an echo.
        WorldState after = sellInto(Params.defaults(), 16, 0).finalState();

        NavigableSet<Integer> chain = after.villager(0).belief(ABUNDANT).chain();
        assertTrue(chain.contains(Belief.SEEN), "the source should be the sight of it");
        assertFalse(chain.contains(Belief.MARKET), "the market did not say this");
        assertFalse(chain.contains(0), "nobody sits in their own chain");
    }

    @Test
    void aVillageAlreadySureOfAShortageIsHarderToTalkRoundWithOneSale() {
        Params params = Params.defaults();
        int trader = 0;
        Run convinced = Run.execute(42, params, List.of(
                new PlantRumor(1, SCARCE, 3, trader),
                new PlayerTraded(2, trader, 16, 128, watching(trader))), 3);
        Run openMinded = sellInto(params, 16, trader);

        double stubborn = convinced.finalState().villager(trader).belief(ABUNDANT).confidence();
        double persuaded = openMinded.finalState().villager(trader).belief(ABUNDANT).confidence();

        assertTrue(stubborn < persuaded,
                "somebody certain of a shortage should not drop it at one sale: "
                        + stubborn + " against " + persuaded);
    }

    @Test
    void aTradeReplaysToTheSameVillage() {
        Run run = sellInto(Params.defaults(), 16, 0, 1, 2);

        assertEquals(run.finalState(), Simulation.replay(run.log()));
    }

    @Test
    void aTradeSurvivesBeingWrittenDownAndReadBack() throws Exception {
        Run run = sellInto(Params.defaults(), 16, 0, 1, 2);
        java.nio.file.Path file = java.nio.file.Files.createTempFile("hearsay", ".recipe");

        RecipeFile.write(run, file);
        Run reread = RecipeFile.read(file);

        assertEquals(run.inputs(), reread.inputs(), "the witnesses decide who learned what");
        assertEquals(run.finalState(), reread.finalState());
    }
}
