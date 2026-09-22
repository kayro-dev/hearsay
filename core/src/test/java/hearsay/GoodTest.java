package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** Goods as data, and the seed contract that lets diamond stay what it was. */
class GoodTest {

    @Test
    void diamondDrawsExactlyTheNumbersItDrewBeforeThereWereOtherGoods() {
        // Why every experiment from E1 to E38 still stands. PinnedLogsTest catches a change
        // in the whole log; this names the cause if it is the streams.
        for (RandomStream stream : RandomStream.values()) {
            Random before = stream.from(42);
            Random now = stream.from(42, Good.DIAMOND);
            for (int draw = 0; draw < 100; draw++) {
                assertEquals(before.nextLong(), now.nextLong(),
                        stream + " for diamond drifted from its pre-stage-2 stream at draw " + draw);
            }
        }
    }

    @Test
    void diamondsRumourIdsStartWhereTheyAlwaysDid() {
        assertEquals(0, Good.DIAMOND.firstRumorId(),
                "every diamond rumour id recorded before stage 2 would be renumbered");
        assertEquals(0, new WorldState().nextRumorId(Good.DIAMOND));
    }

    @Test
    void aGoodIsFoundByTheNameClaimsAlreadyUse() {
        assertEquals(Good.DIAMOND, Good.of("diamond"));
        assertEquals(Good.DIAMOND, Good.of(new Claim(Simulation.DIAMOND, ClaimType.SCARCE)));
        assertThrows(IllegalArgumentException.class, () -> Good.of("emerald"),
                "emeralds are the currency, never a good");
    }

    @Test
    void aGoodKnowsWhatSeveralOfItAreCalled() {
        assertEquals("diamonds", Good.DIAMOND.plural());
        assertEquals("gold ingots", Good.GOLD.plural());
        assertEquals("iron ingots", Good.IRON.plural());
        assertEquals("wheat", Good.WHEAT.plural(), "wheat is a mass noun, not 'wheats'");
        assertEquals("120 wheat", Good.WHEAT.amount(120));
    }

    @Test
    void everyGoodsBundleFitsInOneTrade() {
        // A trade holds at most two stacks of 64. A good whose bundle could not be offered
        // should fail here, not in front of a player at a counter that will not fill.
        for (Good good : Good.values()) {
            int[] stacks = good.stacks(64);
            assertTrue(stacks.length <= 2, good + " needs " + stacks.length + " slots");
            int total = 0;
            for (int stack : stacks) {
                assertTrue(stack >= 1 && stack <= 64, good + " has a stack of " + stack);
                total += stack;
            }
            assertEquals(good.bundle(), total, good + " lost part of its bundle in the split");
        }
    }

    @Test
    void wheatSplitsIntoTwoEqualStacks() {
        assertArrayEquals(new int[] {60, 60}, Good.WHEAT.stacks(64));
        assertArrayEquals(new int[] {1}, Good.DIAMOND.stacks(64));
        assertArrayEquals(new int[] {32}, Good.IRON.stacks(64));
    }

    @Test
    void oneOfSomethingIsNotSeveralOfIt() {
        // A diamond bundle is one diamond, so every diamond sale hits this.
        assertEquals("1 diamond", Good.DIAMOND.amount(1));
        assertEquals("12 diamonds", Good.DIAMOND.amount(12));
        assertEquals("1 gold ingot", Good.GOLD.amount(1));
        assertEquals("24 gold ingots", Good.GOLD.amount(24));
    }

    @Test
    void noTwoGoodsShareDice() {
        // If two goods ever drew from the same stream, one would shift the other's draws.
        for (RandomStream stream : RandomStream.values()) {
            java.util.Set<Long> firstDraws = new java.util.HashSet<>();
            for (Good good : Good.values()) {
                assertTrue(firstDraws.add(stream.from(42, good).nextLong()),
                        stream + " gives " + good + " the same dice as another good");
            }
        }
    }

    @Test
    void everyGoodIsAtVanillaValueExceptDiamond() {
        // What stops a farm becoming an emerald printer: vanilla pays a third of an emerald
        // for gold and a quarter for iron, and so must Hearsay when nobody believes anything.
        assertEquals(8.0, Good.DIAMOND.emeraldsEach(), 1e-12, "diamond is repriced on purpose");
        assertEquals(1.0 / 3, Good.GOLD.emeraldsEach(), 1e-12);
        assertEquals(1.0 / 4, Good.IRON.emeraldsEach(), 1e-12);
        assertEquals(1.0 / 20, Good.WHEAT.emeraldsEach(), 1e-12,
                "wheat at six for 120 is exactly vanilla's twenty to the emerald");
    }

    @Test
    void aRumourIdSaysWhichGoodItIsAbout() {
        for (Good good : Good.values()) {
            assertEquals(good, Good.ofRumor(good.firstRumorId()));
            assertEquals(good, Good.ofRumor(good.firstRumorId() + 12_345));
        }
    }
}
