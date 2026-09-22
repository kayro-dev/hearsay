package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Telling what the lie did from what the player's own selling did. */
class FootprintTest {

    private static final Claim SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);
    private static final int TICKS = 200;

    private static Run played(boolean lie, boolean trades) {
        Params params = Params.defaults();
        int planter = Run.execute(42, params, List.of(), 1)
                .finalState().gossipiestVillager().id();

        List<Input> inputs = new ArrayList<>();
        if (lie) {
            inputs.add(new PlantRumor(1, SCARCE, 1, planter));
        }
        if (trades) {
            for (long tick = 60; tick < 120; tick += 8) {
                inputs.add(new PlayerTraded(tick, 0, Simulation.DIAMOND, 16, 128, new TreeSet<>(List.of(0, 1, 2))));
            }
        }
        return Run.execute(42, params, inputs, TICKS);
    }

    @Test
    void theFourTimelinesAreTheFourThingsThatCouldHaveHappened() {
        Footprint footprint = Footprint.of(played(true, true), SCARCE);

        assertEquals(played(false, true).log().size(),
                Run.execute(42, Params.defaults(), stripLies(played(true, true)), TICKS)
                        .log().size(),
                "the no-lie timeline should be the run with the lie taken out");
        assertEquals(footprint.leftAlone().peakPrice(),
                MarketStats.of(played(false, false).log(), SCARCE).peakPrice(),
                "the left-alone timeline should be the village with nothing done to it");
    }

    private static List<Input> stripLies(Run run) {
        List<Input> kept = new ArrayList<>();
        for (Input input : run.inputs()) {
            if (!(input instanceof PlantRumor)) {
                kept.add(input);
            }
        }
        return kept;
    }

    @Test
    void theLieIsCreditedWithWhatTheLieDid() {
        Footprint footprint = Footprint.of(played(true, false), SCARCE);

        assertTrue(footprint.peakFromTheLie() > 0, "the lie moved the price");
        assertEquals(0, footprint.peakFromTheTrading(),
                "nobody traded, so no part of this can be the trading");
    }

    @Test
    void theTradingIsNotCreditedToTheLie() {
        // The trap this class exists for. A village nobody lied to, traded with heavily:
        // every point the price moved is the player's own doing, and none of it may be
        // reported as the lie's.
        Footprint footprint = Footprint.of(played(false, true), SCARCE);

        assertEquals(0, footprint.peakFromTheLie(),
                "nobody lied, so the lie cannot have earned anything");
    }

    @Test
    void whenBothHappenedNeitherSwallowsTheOther() {
        Footprint footprint = Footprint.of(played(true, true), SCARCE);

        assertEquals(footprint.asPlayed().peakPrice() - footprint.withoutTheLie().peakPrice(),
                footprint.peakFromTheLie());
        assertEquals(footprint.asPlayed().peakPrice() - footprint.withoutTheTrades().peakPrice(),
                footprint.peakFromTheTrading());
        // The two are measured against different timelines on purpose, so they are not
        // required to sum to the whole. What is left over is the interaction, and it is
        // reported rather than quietly assigned to one of them.
        assertEquals(footprint.interaction(),
                footprint.asPlayed().peakPrice() - footprint.withoutTheLie().peakPrice()
                        - footprint.withoutTheTrades().peakPrice()
                        + footprint.leftAlone().peakPrice());
    }
}
