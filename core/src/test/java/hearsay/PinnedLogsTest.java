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
 */
class PinnedLogsTest {

    private static final Claim SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

    private static String checksum(List<Event> log) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Event event : log) {
            digest.update((event + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest()).substring(0, 16);
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
            inputs.add(new PlayerTraded(tick, 0, 16, 128, near(0, 1, 2)));
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
            inputs.add(new PlayerTraded(tick, 3, 12, 96, near(3, 4)));
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
