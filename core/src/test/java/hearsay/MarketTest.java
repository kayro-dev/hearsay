package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Belief moves the price, and the price moves belief. */
class MarketTest {

    private static final int TICKS = 200;
    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    private static Run quietVillage(Params params) {
        return Run.execute(42, params, List.of(), TICKS);
    }

    private static List<MarketPriceSet> prices(List<Event> log) {
        List<MarketPriceSet> found = new ArrayList<>();
        for (Event event : log) {
            if (event instanceof MarketPriceSet e) {
                found.add(e);
            }
        }
        return found;
    }

    @Test
    void withoutRumorsOrNoiseThePriceNeverLeavesBase() {
        Params silent = Params.defaults().withMarketNoise(0);
        // With no step, the carried-over wobble stays at zero for the whole run.

        Run run = quietVillage(silent);

        List<MarketPriceSet> settled = prices(run.log());
        assertFalse(settled.isEmpty(), "the market should have opened at least once");
        for (MarketPriceSet price : settled) {
            assertEquals(silent.basePrice(), price.price(),
                    "tick " + price.tick() + ": nobody believes anything, so nothing moves");
        }
        // ...and with the price pinned at base, nobody can read anything into it.
        for (Event event : run.log()) {
            assertFalse(event instanceof PriceObserved, "there was nothing to observe");
        }
    }

    @Test
    void aMinorityOfBelieversStillMovesThePrice() {
        // E19: the price used to be the median ask, which asks only which side of the
        // middle a villager falls on. A quarter of the market believing the worst moved it
        // by nothing at all, which is what kept every played session pinned at base.
        Params params = Params.defaults()
                .withMarketNoise(0)
                .withMeetingSource(MeetingSource.EXTERNAL)
                .withVillagers(20);

        List<Input> inputs = new ArrayList<>();
        int inTheMarket = 8;
        for (int id = 0; id < params.villagers(); id++) {
            inputs.add(new VillagerSeen(1, id, id < inTheMarket ? Spot.MARKET : Spot.HOME));
        }
        // Two of the eight standing there, a clear minority on either side of the middle.
        inputs.add(new PlantRumor(1, DIAMONDS_SCARCE, 1, 0));
        inputs.add(new PlantRumor(1, DIAMONDS_SCARCE, 1, 1));

        List<MarketPriceSet> settled = prices(Run.execute(42, params, inputs, 1).log());

        assertEquals(1, settled.size(), "the market should have settled exactly one price");
        MarketPriceSet price = settled.get(0);
        assertEquals(inTheMarket, price.askingVillagers());
        // Six asking 100 and two asking 175, which averages 118.75.
        assertEquals(119, price.price(),
                "a quarter of the market believing the worst has to show in the price");
    }

    @Test
    void theMarketOnlyOpensWithAQuorum() {
        Run run = quietVillage(Params.defaults());

        Params params = Params.defaults();
        for (MarketPriceSet price : prices(run.log())) {
            assertTrue(price.askingVillagers() >= params.marketQuorum(),
                    "tick " + price.tick() + " settled a price with " + price.askingVillagers());
        }
    }

    @Test
    void onlyVillagersStandingAtTheMarketReadThePrice() {
        Run run = Run.execute(42, Params.defaults(),
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 2, 10)), TICKS);

        WorldState mirror = new WorldState();
        int observations = 0;
        for (Event event : run.log()) {
            if (event instanceof PriceObserved e) {
                assertEquals(Spot.MARKET, mirror.villager(e.villagerId()).spot(),
                        "tick " + e.tick() + ": " + e.villagerId() + " read the price from elsewhere");
                observations++;
            }
            mirror.apply(event);
        }
        assertTrue(observations > 0, "somebody should have read the price by now");
    }

    @Test
    void moreScarcityBeliefAlwaysMeansAHigherAsk() {
        Params params = Params.defaults();
        double[] confidences = {0.0, 0.1, 0.25, 0.5, 0.75, 0.9, 1.0};

        // Along each axis separately: the two do not form a single order, since being
        // half sure of the worst version outweighs being certain of the mildest.
        for (int severity = 1; severity <= Rumor.MAX_SEVERITY; severity++) {
            double previous = -1;
            for (double confidence : confidences) {
                double ask = askWith(params, confidence, severity);
                assertTrue(ask >= previous - 1e-9,
                        "ask fell as confidence rose at severity " + severity);
                previous = ask;
            }
        }
        for (double confidence : confidences) {
            double previous = -1;
            for (int severity = 1; severity <= Rumor.MAX_SEVERITY; severity++) {
                double ask = askWith(params, confidence, severity);
                assertTrue(ask >= previous - 1e-9,
                        "ask fell as severity rose at confidence " + confidence);
                previous = ask;
            }
        }
        assertTrue(askWith(params, 0, 1) < askWith(params, 1, 1), "belief must move the ask");
        assertEquals(params.basePrice(), askWith(params, 0, 1), 1e-9,
                "believing nothing asks the base price");
    }

    /** The ask of a villager holding one scarcity belief at a given strength. */
    private static double askWith(Params params, double confidence, int severity) {
        Simulation sim = new Simulation(42, params, List.of());
        sim.run(1);
        Villager villager = sim.state().villager(0);
        if (confidence > 0) {
            sim.state().apply(new RumorPlanted(1, 900 + severity, DIAMONDS_SCARCE,
                    severity, 0, confidence));
        }
        return sim.askingPrice(villager, Good.DIAMOND);
    }

    @Test
    void anAbundantBeliefPullsTheAskBelowBase() {
        Params params = Params.defaults();
        Simulation sim = new Simulation(42, params, List.of());
        sim.run(1);

        sim.state().apply(new RumorPlanted(1, 900,
                new Claim(Simulation.DIAMOND, ClaimType.ABUNDANT), 2, 0, 0.8));

        assertTrue(sim.askingPrice(sim.state().villager(0), Good.DIAMOND) < params.basePrice());
    }

    @Test
    void replayRebuildsThePrice() {
        Run run = Run.execute(42, Params.defaults(),
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 2, 10)), TICKS);

        WorldState replayed = Simulation.replay(run.log());

        assertTrue(replayed.marketPrice(Good.DIAMOND).isPresent(), "the market should have settled");
        assertEquals(run.finalState(), replayed);
        assertEquals(run.finalState().marketPrice(Good.DIAMOND), replayed.marketPrice(Good.DIAMOND));
    }

    @Test
    void plantingARumorStillChangesNothingAboutWhereAnyoneWalks() {
        List<Event> withRumor = Run.execute(42, Params.defaults(),
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 2, 10)), TICKS).log();
        List<Event> without = Run.execute(42, Params.defaults(), List.of(), TICKS).log();

        assertEquals(physical(without), physical(withRumor),
                "movement and meetings must survive the market loop");
        assertNotEquals(without, withRumor, "but the rumor must still change what happens");
    }

    @Test
    void aSteadyPriceIsNoEvidenceHoweverHighItIs() {
        Run run = Run.execute(3, Params.defaults(),
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 2, 12)), TICKS);

        // Every observation must be a move away from that villager's own anchor, never a
        // reading of the level. This is what stops a high plateau confirming itself.
        WorldState mirror = new WorldState();
        int observations = 0;
        for (Event event : run.log()) {
            if (event instanceof PriceObserved e) {
                int anchor = mirror.villager(e.villagerId()).lastObservedPrice(Good.DIAMOND)
                        .orElse(Params.defaults().basePrice());
                double move = Math.abs(e.price() - anchor) / (double) anchor;
                assertTrue(move > Params.defaults().observationThreshold(),
                        "tick " + e.tick() + ": read " + e.price() + " against anchor " + anchor);
                observations++;
            }
            mirror.apply(event);
        }
        assertTrue(observations > 0);
    }

    @Test
    void aRisingPriceMeansScarcityAndAFallingOneMeansPlenty() {
        Run run = Run.execute(3, Params.defaults(),
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 2, 12)), TICKS);

        WorldState mirror = new WorldState();
        int rises = 0;
        int falls = 0;
        for (Event event : run.log()) {
            if (event instanceof PriceObserved e) {
                int anchor = mirror.villager(e.villagerId()).lastObservedPrice(Good.DIAMOND)
                        .orElse(Params.defaults().basePrice());
                if (e.price() > anchor) {
                    assertEquals(ClaimType.SCARCE, e.claim().type(), "a rise means scarcity");
                    rises++;
                } else {
                    assertEquals(ClaimType.ABUNDANT, e.claim().type(), "a fall means plenty");
                    falls++;
                }
            }
            mirror.apply(event);
        }
        assertTrue(rises > 0, "the price should have risen on somebody");
        assertTrue(falls > 0, "and come back down on somebody, or nothing ever deflates");
    }

    @Test
    void observingUpdatesTheAnchorToWhatWasSeen() {
        Run run = Run.execute(3, Params.defaults(),
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 2, 12)), TICKS);

        WorldState mirror = new WorldState();
        int checked = 0;
        for (Event event : run.log()) {
            mirror.apply(event);
            if (event instanceof PriceObserved e) {
                assertEquals(e.price(),
                        mirror.villager(e.villagerId()).lastObservedPrice(Good.DIAMOND).orElseThrow());
                checked++;
            }
        }
        assertTrue(checked > 0);
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
}
