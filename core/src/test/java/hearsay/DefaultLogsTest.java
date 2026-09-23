package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * The same five diamond villages as {@link PinnedLogsTest}, pinned under the defaults since
 * E47 made the level gate one of them (2026-09-23).
 *
 * <p>{@link PinnedLogsTest} holds the logs recorded before stage 2, under the rule they were
 * recorded with, and keeps proving what it always proved. These hold the model as it is now:
 * any change to what a diamond village does under the shipped defaults fails here. The same
 * rule applies — <strong>do not update these to match a change</strong>. A deliberate change to
 * the defaults gets new pins beside these, with the date and the E-number that justified it,
 * the way these sit beside the originals.
 */
class DefaultLogsTest {

    private static final Params NOW = Params.defaults();

    @Test
    void theseAreTheDefaultsWithTheGate() {
        assertEquals(1.0, NOW.levelGate());
        assertEquals(0.0, NOW.trendAnchor());
    }

    @Test
    void aLieTheOrdinaryWay() throws Exception {
        assertEquals("1e2a8b1b2836abcb", PinnedLogsTest.checksum(PinnedLogsTest.aLieTheOrdinaryWay(NOW).log()));
    }

    @Test
    void anotherSeedAnotherLie() throws Exception {
        assertEquals("38ccfe1febc89f03", PinnedLogsTest.checksum(PinnedLogsTest.anotherSeedAnotherLie(NOW).log()));
    }

    @Test
    void theVillageThatUsedToPanicWithNobodyLyingToIt() throws Exception {
        // Seed 1165 bubbled unaided under the old reading; under the gate it is one of the
        // villages E47 found no longer does.
        Run run = PinnedLogsTest.aVillageThatPanicsWithNobodyLyingToIt(NOW);
        assertTrue(MarketStats.of(run.log(), new Claim(Simulation.DIAMOND, ClaimType.SCARCE))
                .bubbles().isEmpty(), "seed 1165 should no longer bubble unaided");
        assertEquals("dfca35e2e7044a92", PinnedLogsTest.checksum(run.log()));
    }

    @Test
    void aLieAndAPlayerSellingIntoIt() throws Exception {
        assertEquals("79d4c447c215c764", PinnedLogsTest.checksum(
                PinnedLogsTest.aLieAndAPlayerSellingIntoIt(NOW).log()));
    }

    @Test
    void everythingSwitchedOnAtOnce() throws Exception {
        assertEquals("0ba86cd0ed635ea2", PinnedLogsTest.checksum(
                PinnedLogsTest.everythingSwitchedOnAtOnce(NOW).log()));
    }
}
