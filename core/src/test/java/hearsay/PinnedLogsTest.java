package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Five diamond villages, pinned bit for bit before stage 2 added any other good.
 *
 * <p>Stage 2 turns one market into several, and nearly everything it touches — the random
 * streams, the rumour ids, who gets to speak in a meeting, the market's price and noise —
 * is something diamond's behaviour runs through. The promise is that none of it changes
 * diamond: every experiment from E1 to E38 was measured on diamond alone, and they stand
 * only if a diamond-only village produces exactly the log it always did.
 *
 * <p>So this does not ask whether diamond behaves <em>similarly</em>. It asks whether every
 * event, in order, with every number, is identical to the log recorded on 2026-09-22 before
 * the first line of stage 2 was written. A checksum rather than a stored log, because the
 * logs run to twenty thousand events; the counts beside each one say which paths it covers,
 * so a failure can be read before it is debugged.
 *
 * <p><strong>If this fails, stage 2 has changed diamond.</strong> Do not update the
 * checksums to match. Find what diamond is now reading that it did not read before.
 *
 * <p><strong>One deliberate exception, and why it is not an update.</strong> Stage 2 gave
 * {@link MarketPriceSet} and {@link MarketNoiseSet} an {@code item}, because once there are
 * several markets an event has to say which one it settled. That changes how those two
 * events print, and so would change any checksum of their text — without changing a single
 * decision. So the checksum is taken over each event in the form it had when these were
 * pinned: {@link #frozen(Event)} prints those two without the new field and every other
 * event exactly as it prints. The checksums themselves are the ones recorded on 2026-09-22
 * and have not been touched.
 *
 * <p>Stripping a field would hide it, so the field is checked rather than trusted:
 * {@link #onlyDiamondWasPriced} asserts that every market event in these runs names
 * diamond. Together the two say the whole of it — every event, every number and every
 * order is what it was, and the one addition carries no information in a diamond-only
 * village. Stage 2 step 1 was checked this way before the renderer was written into the
 * test: all five logs matched and no market event named another good.
 */
class PinnedLogsTest {

    private static final Claim SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    private static String checksum(List<Event> log) throws Exception {
        onlyDiamondWasPriced(log);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Event event : log) {
            digest.update((frozen(event) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest()).substring(0, 16);
    }

    /**
     * An event as it printed when these logs were pinned. Only the two market events have
     * changed shape since, and only by gaining the good they are about; everything else is
     * printed exactly as it is.
     *
     * <p><strong>This printer is part of the guarantee, frozen as the checksums are.</strong>
     * The checksums only mean anything because this prints what was printed on 2026-09-22.
     * Never change it to make a test pass: a printer that drifts can make a changed log
     * hash to the old value, and then the pins prove nothing while looking as though they
     * prove everything. {@link #thePinnedPrinterStillPrintsWhatItPrinted} fails if its
     * output for a known event moves by a character.
     *
     * <p>Private to this class on purpose. Nothing else may print events this way, so
     * nothing else can come to depend on it and argue for changing it.
     */
    private static String frozen(Event event) {
        return switch (event) {
            case MarketPriceSet m -> "MarketPriceSet[tick=" + m.tick() + ", price=" + m.price()
                    + ", askingVillagers=" + m.askingVillagers() + "]";
            case MarketNoiseSet n -> "MarketNoiseSet[tick=" + n.tick() + ", level=" + n.level() + "]";
            default -> event.toString();
        };
    }

    /** The field {@link #frozen} leaves out has to carry nothing, or leaving it out hides it. */
    private static void onlyDiamondWasPriced(List<Event> log) {
        for (Event event : log) {
            String item = switch (event) {
                case MarketPriceSet m -> m.item();
                case MarketNoiseSet n -> n.item();
                default -> Simulation.DIAMOND;
            };
            assertEquals(Simulation.DIAMOND, item,
                    "a diamond-only village priced something else: " + event);
        }
    }

    private static int planter(long seed, Params params) {
        return Run.execute(seed, params, List.of(), 1).finalState().gossipiestVillager().id();
    }

    private static NavigableSet<Integer> near(int... ids) {
        NavigableSet<Integer> set = new TreeSet<>();
        for (int id : ids) {
            set.add(id);
        }
        return set;
    }

    @Test
    void thePinnedPrinterStillPrintsWhatItPrinted() {
        // The literal text, not a recomputation of it: this is what the two market events
        // looked like when the five logs were pinned, down to the spacing.
        assertEquals("MarketPriceSet[tick=5, price=101, askingVillagers=11]",
                frozen(new MarketPriceSet(5, 101, 11, Simulation.DIAMOND)));
        assertEquals("MarketNoiseSet[tick=5, level=0.015]",
                frozen(new MarketNoiseSet(5, 0.015, Simulation.DIAMOND)));
        // And everything else falls through to the event's own printing, untouched. If a
        // record's printing ever changes, the checksums are what catch it — not this.
        DayEnded night = new DayEnded(4, 0.92, 0.05);
        assertEquals(night.toString(), frozen(night));
    }

    @Test
    void aLieTheOrdinaryWay() throws Exception {
        // 39 tellings, 2 mutations, 82 price readings.
        Params params = Params.defaults();
        Run run = Run.execute(42, params,
                List.of(new PlantRumor(1, SCARCE, 1, planter(42, params))), 200);

        assertEquals("df88fdc5bc65a8a8", checksum(run.log()));
    }

    @Test
    void anotherSeedAnotherLie() throws Exception {
        // 102 tellings, 7 mutations, 131 price readings.
        Params params = Params.defaults();
        Run run = Run.execute(1001, params,
                List.of(new PlantRumor(1, SCARCE, 1, planter(1001, params))), 200);

        assertEquals("c3555d5d8a6f8a73", checksum(run.log()));
    }

    @Test
    void aVillageThatPanicsWithNobodyLyingToIt() throws Exception {
        // One of the few seeds that bubble unaided. 700 ticks, 261 tellings of rumours the
        // village invented from its own price, 316 readings.
        Run run = Run.execute(1165, Params.defaults(), List.of(), 700);

        assertEquals("cd3f613454132d75", checksum(run.log()));
    }

    @Test
    void aLieAndAPlayerSellingIntoIt() throws Exception {
        // Stage 3's path: 30 villagers taking in a sale they watched.
        Params params = Params.defaults();
        List<Input> inputs = new ArrayList<>(
                List.of(new PlantRumor(1, SCARCE, 1, planter(2160, params))));
        for (long tick = 60; tick < 140; tick += 8) {
            inputs.add(new PlayerTraded(tick, 0, Simulation.DIAMOND, 16, 128, near(0, 1, 2)));
        }
        Run run = Run.execute(2160, params, inputs, 200);

        assertEquals("ce423029018096f6", checksum(run.log()));
    }

    @Test
    void everythingSwitchedOnAtOnce() throws Exception {
        // A severity-2 lie, trades, and reality checks turned on although they are off by
        // default, so the one rule that pulls rather than pushes is pinned too: 474
        // tellings, 20 mutations, 16 witnessed sales, 447 checks.
        Params params = Params.defaults().withCheckWeight(0.30);
        List<Input> inputs = new ArrayList<>(
                List.of(new PlantRumor(1, SCARCE, 2, planter(7, params))));
        for (long tick = 40; tick < 120; tick += 10) {
            inputs.add(new PlayerTraded(tick, 3, Simulation.DIAMOND, 12, 96, near(3, 4)));
        }
        for (long tick = 4; tick <= 200; tick += 4) {
            for (int villager = 0; villager < 5; villager++) {
                inputs.add(new RealityChecked(tick, villager, Simulation.DIAMOND, 20));
            }
        }
        inputs.sort(Comparator.comparingLong(Input::tick));
        Run run = Run.execute(7, params, inputs, 200);

        assertEquals("f6c036da458d28da", checksum(run.log()));
    }
}
