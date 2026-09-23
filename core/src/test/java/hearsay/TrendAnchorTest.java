package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Reading the price against its own recent trend (E46), and staying out of the way when off. */
class TrendAnchorTest {

    private static final Claim DIAMONDS_SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    private static WorldState pricedAt(int... prices) {
        WorldState world = new WorldState();
        for (int i = 0; i < prices.length; i++) {
            world.apply(new MarketPriceSet(i + 1, prices[i], 5, Simulation.DIAMOND));
        }
        return world;
    }

    @Test
    void theTrendIsTheMeanOfTheWindowBeforeThisTick() {
        WorldState world = pricedAt(100, 110, 120, 130, 140); // ticks 1 to 5
        // Reading at tick 5, a window of three: ticks 2, 3 and 4. Tick 5 is the price
        // being judged and must not be part of what it is judged against.
        assertEquals(120.0, world.trailingPrice(Good.DIAMOND, 5, 3).orElseThrow(), 1e-12);
        assertEquals(115.0, world.trailingPrice(Good.DIAMOND, 5, 4).orElseThrow(), 1e-12);
        assertEquals(100.0, world.trailingPrice(Good.DIAMOND, 2, 16).orElseThrow(), 1e-12);
    }

    @Test
    void aMarketShutForTheWholeWindowFallsBackToItsLastPrice() {
        WorldState world = pricedAt(100, 90);
        assertEquals(90.0, world.trailingPrice(Good.DIAMOND, 40, 4).orElseThrow(), 1e-12);
        assertTrue(world.trailingPrice(Good.GOLD, 40, 4).isEmpty(), "gold never traded");
        assertTrue(new WorldState().trailingPrice(Good.DIAMOND, 5, 4).isEmpty());
    }

    @Test
    void offMeansTheWindowIsNeverRead() {
        // With trendAnchor at 0 the window is irrelevant: today's rule, exactly.
        int planter = Run.execute(42, Params.defaults(), List.of(), 1)
                .finalState().gossipiestVillager().id();
        List<Input> lie = List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, planter));
        assertEquals(Run.execute(42, Params.defaults(), lie, 200).log(),
                Run.execute(42, Params.defaults().withTrendWindowTicks(3), lie, 200).log());
    }

    @Test
    void onItChangesWhatTheVillageConcludes() {
        // Not a claim about which way: that is E46's to measure. Only that the setting is
        // wired to something, so a sweep of it is not a sweep of nothing.
        int planter = Run.execute(42, Params.defaults(), List.of(), 1)
                .finalState().gossipiestVillager().id();
        List<Input> lie = List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, planter));
        assertNotEquals(Run.execute(42, Params.defaults(), lie, 200).log(),
                Run.execute(42, Params.defaults().withTrendAnchor(1.0), lie, 200).log());
    }

    @Test
    void aWorldRebuiltFromItsLogReadsTheSameTrend() {
        // The history lives in WorldState, so a world rebuilt from the log alone must carry
        // on exactly as the live one does with the trend switched on.
        Params params = Params.defaults().withTrendAnchor(1.0);
        int planter = Run.execute(42, params, List.of(), 1).finalState().gossipiestVillager().id();
        Simulation live = new Simulation(42, params,
                List.of(new PlantRumor(1, DIAMONDS_SCARCE, 1, planter)));
        live.run(60);
        List<Event> prefix = List.copyOf(live.log());
        Simulation fromLive = live.fork(777, List.of());
        fromLive.run(60);
        Simulation fromReplay = Simulation.resume(Simulation.replay(prefix), 777, params, List.of());
        fromReplay.run(60);

        assertEquals(fromLive.log(), fromReplay.log());
        assertEquals(fromLive.state(), fromReplay.state());
    }

    @Test
    void theSettingSurvivesARecipeAndAnOldRecipeReadsAsOff(@org.junit.jupiter.api.io.TempDir
                                                            java.nio.file.Path folder)
            throws Exception {
        java.nio.file.Path file = folder.resolve("trend.hearsay");
        Params params = Params.defaults().withTrendAnchor(0.5).withTrendWindowTicks(9);
        RecipeFile.write(Run.execute(42, params, List.of(), 20), file);
        Params read = RecipeFile.read(file).params();
        assertEquals(0.5, read.trendAnchor());
        assertEquals(9, read.trendWindowTicks());

        // A recipe written before the setting existed was played under today's rule.
        String old = java.nio.file.Files.readString(file)
                .replaceAll(" trendAnchor=\\S+", "").replaceAll(" trendWindowTicks=\\S+", "");
        java.nio.file.Files.writeString(file, old);
        assertEquals(0.0, RecipeFile.read(file).params().trendAnchor());
    }
}
