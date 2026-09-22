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
    }
}
