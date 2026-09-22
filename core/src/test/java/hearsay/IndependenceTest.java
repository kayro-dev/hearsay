package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.Random;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Every good is its own market, and nothing about one can disturb another.
 *
 * <p>Guaranteed rather than measured. A sweep that finds no leakage has only failed to find
 * it; this runs the same village with and without a second good and demands that the first
 * good's events be <em>equal</em> — every event, every number, every order — not merely
 * similar. A lie about gold that moved diamond by one rumour id, one draw, one conversation,
 * fails here.
 *
 * <p>Events that belong to no good — the clock, villagers being born, walking and meeting,
 * the day ending — are kept in the comparison, since a good that disturbed who met whom
 * would be the worst leak of all.
 */
class IndependenceTest {

    private static final Claim DIAMONDS_SCARCE = new Claim(Good.DIAMOND.id(), ClaimType.SCARCE);
    private static final Claim GOLD_SCARCE = new Claim(Good.GOLD.id(), ClaimType.SCARCE);
    private static final int TICKS = 200;

    /**
     * Which good an event is about, or null for one that belongs to nobody. An exhaustive
     * switch over a sealed interface, so a new kind of event cannot be added without
     * deciding here which market it belongs to.
     */
    private static Good goodOf(Event event) {
        return switch (event) {
            case TickStarted e -> null;
            case VillagerCreated e -> null;
            case VillagerMoved e -> null;
            case VillagersMet e -> null;
            case DayEnded e -> null;
            case RumorPlanted e -> Good.of(e.claim());
            case RumorMutated e -> Good.ofRumor(e.rumorId());
            case RumorTold e -> Good.ofRumor(e.toldRumorId());
            case MarketNoiseSet e -> Good.of(e.item());
            case MarketPriceSet e -> Good.of(e.item());
            case PriceObserved e -> Good.of(e.claim());
            case TradeSeen e -> Good.of(e.claim());
            case StockChecked e -> Good.of(e.claim());
        };
    }

    /** One good's events, and everyone's, in the order they happened. */
    private static List<Event> seenBy(Good good, List<Event> log) {
        List<Event> kept = new ArrayList<>();
        for (Event event : log) {
            Good about = goodOf(event);
            if (about == null || about == good) {
                kept.add(event);
            }
        }
        return kept;
    }

    /**
     * A lie about this good, a player selling it into the panic, and villagers looking —
     * using only villagers the village actually has, since the fuzzed settings go down to
     * a village of two.
     */
    private static List<Input> everythingAbout(Good good, int planter, int villagers) {
        List<Input> inputs = new ArrayList<>();
        inputs.add(new PlantRumor(1, new Claim(good.id(), ClaimType.SCARCE), 1, planter));
        NavigableSet<Integer> watching = new TreeSet<>();
        for (int id = 0; id < Math.min(3, villagers); id++) {
            watching.add(id);
        }
        for (long tick = 60; tick < 140; tick += 8) {
            inputs.add(new PlayerTraded(tick, 0, good.id(), good.bundle(),
                    good.normalEmeralds(), watching));
        }
        for (long tick = 4; tick <= TICKS; tick += 4) {
            for (int villager = 0; villager < Math.min(5, villagers); villager++) {
                inputs.add(new RealityChecked(tick, villager, good.id(), 20));
            }
        }
        return inputs;
    }

    private static List<Input> sorted(List<Input> inputs) {
        List<Input> copy = new ArrayList<>(inputs);
        copy.sort(Comparator.comparingLong(Input::tick)); // stable, so same-tick order holds
        return copy;
    }

    private static void assertIndependent(long seed, Params base) {
        int planter = Run.execute(seed, base.withGoods(Good.DIAMOND), List.of(), 1)
                .finalState().gossipiestVillager().id();
        List<Input> diamond = everythingAbout(Good.DIAMOND, planter, base.villagers());
        List<Input> gold = everythingAbout(Good.GOLD, (planter + 1) % base.villagers(),
                base.villagers());
        List<Input> both = new ArrayList<>(diamond);
        both.addAll(gold);

        Run diamondAlone = Run.execute(seed, base.withGoods(Good.DIAMOND), sorted(diamond), TICKS);
        Run goldAlone = Run.execute(seed, base.withGoods(Good.GOLD), sorted(gold), TICKS);
        Run together = Run.execute(seed, base.withGoods(Good.DIAMOND, Good.GOLD),
                sorted(both), TICKS);

        assertEquals(seenBy(Good.DIAMOND, diamondAlone.log()), seenBy(Good.DIAMOND, together.log()),
                "seed " + seed + ": diamond behaved differently once gold existed");
        assertEquals(seenBy(Good.GOLD, goldAlone.log()), seenBy(Good.GOLD, together.log()),
                "seed " + seed + ": gold behaved differently once diamond existed");
    }

    @Test
    void diamondIsUntouchedByGoldAndGoldByDiamond() {
        for (long seed : new long[] {42, 1001, 1165, 2160, 7}) {
            assertIndependent(seed, Params.defaults());
        }
    }

    @Test
    void theyStayIndependentWithEverySwitchOn() {
        // Reality checks are off by default, which would leave their per-good path untested.
        for (long seed : new long[] {42, 1001, 7}) {
            assertIndependent(seed, Params.defaults().withCheckWeight(0.30));
        }
    }

    @Test
    void theyStayIndependentOnSettingsNobodyChose() {
        // A fixed fuzz, so a failure names its settings and can be reproduced.
        Random fuzz = new Random(20260922);
        for (int run = 0; run < 25; run++) {
            Params params = Params.defaults()
                    .withTellThreshold(fuzz.nextDouble())
                    .withDailyDecay(0.8 + 0.2 * fuzz.nextDouble())
                    .withObservationWeight(fuzz.nextDouble())
                    .withCheckWeight(fuzz.nextDouble())
                    .withMixing(fuzz.nextDouble())
                    .withVillagers(2 + fuzz.nextInt(Simulation.MOST_VILLAGERS - 1));
            assertIndependent(fuzz.nextLong(), params);
        }
    }

    @Test
    void theComparisonWouldNoticeALeak() {
        // Guard against the test passing because the projection threw everything away.
        Params params = Params.defaults().withGoods(Good.DIAMOND, Good.GOLD);
        Run run = Run.execute(42, params,
                sorted(everythingAbout(Good.DIAMOND, 3, params.villagers())), TICKS);

        List<Event> diamond = seenBy(Good.DIAMOND, run.log());
        assertTrue(diamond.stream().anyMatch(e -> e instanceof RumorTold),
                "the diamond projection should contain diamond's conversations");
        assertTrue(run.log().stream().anyMatch(e -> goodOf(e) == Good.GOLD),
                "gold's market should be running, or there is nothing to be independent of");
        assertTrue(diamond.stream().noneMatch(e -> goodOf(e) == Good.GOLD),
                "and none of it should be in diamond's view");
    }
}
