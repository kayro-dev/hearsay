package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Which claim the screen follows, and proof that following one changes nothing. */
class OnDisplayTest {

    private static final Claim WHEAT_SCARCE = new Claim("wheat", ClaimType.SCARCE);
    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);
    private static final Params EVERY_GOOD = Params.defaults().withMixing(1.0)
            .withGoods(Good.values());
    private static final int TICKS = 80;

    private static List<Input> aWheatLie() {
        int planter = Run.execute(42, EVERY_GOOD, List.of(), 1)
                .finalState().gossipiestVillager().id();
        return List.of(new PlantRumor(1, WHEAT_SCARCE, 1, planter));
    }

    private static List<Claim> everyClaim() {
        List<Claim> claims = new ArrayList<>();
        for (Good good : Good.values()) {
            for (ClaimType type : ClaimType.values()) {
                claims.add(new Claim(good.id(), type));
            }
        }
        return claims;
    }

    @Test
    void watchingAnyClaimChangesNothing() {
        // Every claim read every tick, which is more than any player could ask for. If one
        // read could move the village, every played session would depend on what the
        // player happened to be looking at, and no recipe could replay it.
        Simulation watched = new Simulation(42, EVERY_GOOD, aWheatLie());
        for (int tick = 0; tick < TICKS; tick++) {
            watched.step();
            for (Claim claim : everyClaim()) {
                OnDisplay.of(watched, claim);
            }
        }

        assertEquals(Run.execute(42, EVERY_GOOD, aWheatLie(), TICKS).log(), watched.log(),
                "reading the village for the screen changed what the village did");
    }

    @Test
    void itFollowsTheClaimItIsGivenAndNoOther() {
        Simulation village = new Simulation(42, EVERY_GOOD, aWheatLie());
        village.run(TICKS);
        int planter = ((PlantRumor) aWheatLie().get(0)).villagerId();

        OnDisplay wheat = OnDisplay.of(village, WHEAT_SCARCE);
        OnDisplay diamonds = OnDisplay.of(village, DIAMONDS_SCARCE);

        assertTrue(wheat.confidences().containsKey(planter),
                "the villager lied to about wheat should show on the wheat display");
        assertTrue(diamonds.confidences().isEmpty(),
                "nobody said anything about diamonds, so nobody should show on theirs: "
                        + diamonds.confidences());
        assertTrue(wheat.asks().get(planter) > Params.defaults().basePrice(),
                "a villager who fears a famine should ask more for wheat");
        assertTrue(diamonds.asks().values().stream()
                        .allMatch(ask -> ask == Params.defaults().basePrice()),
                "and the same villager should ask the ordinary price for diamonds");
        assertEquals(village.state().marketPrice(Good.WHEAT), wheat.price());
        assertEquals(village.state().marketPrice(Good.DIAMOND), diamonds.price());
        assertEquals(Good.WHEAT, wheat.good());
    }
}
