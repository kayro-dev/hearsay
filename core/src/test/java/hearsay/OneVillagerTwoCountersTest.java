package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * One villager holding two counters — the Farmer buying wheat and selling bread — with a lie
 * about one good told to that very villager, and the other good's counter never moving.
 *
 * <p>IndependenceTest proves each good's events identical with and without the others across
 * the village. This aims at the one place a leak would be easiest: the same mind, holding
 * beliefs about both goods, asked for a price on each. What that villager charges at a
 * counter is {@link Simulation#askingPrice}, which is exactly what the plugin puts on it.
 */
class OneVillagerTwoCountersTest {

    private static final Params FARM = Params.defaults().withGoods(Good.WHEAT, Good.BREAD);
    private static final int TICKS = 200;

    private static int farmer() {
        return Run.execute(5, FARM, List.of(), 1).finalState().gossipiestVillager().id();
    }

    /** A famine lie told to the Farmer, and the player selling wheat at that counter. */
    private static List<Input> famineAtTheFarmer(Good good) {
        int farmer = farmer();
        List<Input> inputs = new ArrayList<>(List.of(
                new PlantRumor(1, new Claim(good.id(), ClaimType.SCARCE), 2, farmer)));
        if (good.villagerBuys()) {
            for (long tick = 40; tick < 160; tick += 8) {
                inputs.add(new PlayerTraded(tick, farmer, good.id(), good.bundle(),
                        good.normalEmeralds(), new TreeSet<>(List.of(farmer, 0, 1))));
            }
        }
        return inputs;
    }

    private static void assertTheOtherCounterNeverMoves(Good lied, Good other) {
        int farmer = farmer();
        Simulation told = new Simulation(5, FARM, famineAtTheFarmer(lied));
        Simulation untold = new Simulation(5, FARM, List.of());
        boolean liedCounterMoved = false;
        for (int tick = 1; tick <= TICKS; tick++) {
            told.step();
            untold.step();
            Villager atTheCounter = told.state().villager(farmer);
            liedCounterMoved |= told.askingPrice(atTheCounter, lied) > FARM.basePrice();
            assertEquals(untold.askingPrice(untold.state().villager(farmer), other),
                    told.askingPrice(atTheCounter, other), 0.0,
                    "the Farmer's " + other.id() + " price moved at tick " + tick
                            + " after a lie about " + lied.id());
            assertEquals(untold.state().villager(farmer).lastObservedPrice(other),
                    atTheCounter.lastObservedPrice(other));
        }
        assertTrue(liedCounterMoved, "the lie should have reached the Farmer's "
                + lied.id() + " counter, or this proves nothing");
        assertEquals(IndependenceTest.seenBy(other, untold.log()),
                IndependenceTest.seenBy(other, told.log()),
                other.id() + "'s events differ after a lie about " + lied.id());
    }

    @Test
    void aFamineToldToTheFarmerLeavesTheirBreadAlone() {
        assertTheOtherCounterNeverMoves(Good.WHEAT, Good.BREAD);
    }

    @Test
    void aBreadPanicToldToTheFarmerLeavesTheirWheatAlone() {
        assertTheOtherCounterNeverMoves(Good.BREAD, Good.WHEAT);
    }
}
