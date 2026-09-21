package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SpotMapperTest {

    private static SpotMapper.Place at(double x, double z) {
        return new SpotMapper.Place(x, 64, z);
    }

    private static final Optional<SpotMapper.Place> NO_BED = Optional.empty();
    private static final Optional<SpotMapper.Place> NO_JOB = Optional.empty();

    @Test
    void aVillagerStandingAtTheirWorkstationIsAtIt() {
        assertEquals(Spot.MARKET, SpotMapper.spotFor(at(0, 0), NO_BED,
                Optional.of(at(1, 0)), Spot.MARKET));
    }

    @Test
    void aFarmerAtTheirComposterIsInTheFields() {
        // The adapter decides what kind of place a workstation is; this only honours it.
        assertEquals(Spot.FIELDS, SpotMapper.spotFor(at(0, 0), NO_BED,
                Optional.of(at(1, 0)), Spot.FIELDS));
    }

    @Test
    void aVillagerInBedIsAtHome() {
        assertEquals(Spot.HOME, SpotMapper.spotFor(at(0, 0), Optional.of(at(0, 1)),
                NO_JOB, Spot.MARKET));
    }

    @Test
    void aVillagerWithNoJobIsSomewhereElseRatherThanNowhere() {
        assertEquals(SpotMapper.ANYWHERE_ELSE,
                SpotMapper.spotFor(at(0, 0), Optional.of(at(50, 50)), NO_JOB, Spot.MARKET));
    }

    @Test
    void aVillagerWithNoBedIsSomewhereElseRatherThanNowhere() {
        assertEquals(SpotMapper.ANYWHERE_ELSE,
                SpotMapper.spotFor(at(0, 0), NO_BED, Optional.of(at(50, 50)), Spot.MARKET));
    }

    @Test
    void aVillagerWithNeitherIsStillSomewhere() {
        assertEquals(SpotMapper.ANYWHERE_ELSE,
                SpotMapper.spotFor(at(0, 0), NO_BED, NO_JOB, Spot.MARKET));
    }

    @Test
    void aVillagerWhoHasWanderedOffFromAJobSiteIsSomewhereElse() {
        // The job site exists and is theirs; they are simply not at it. This is the common
        // case during the day, and it must not quietly count as being at work.
        assertEquals(SpotMapper.ANYWHERE_ELSE, SpotMapper.spotFor(at(0, 0), Optional.of(at(40, 0)),
                Optional.of(at(30, 0)), Spot.MARKET));
    }

    @Test
    void theNearerOfBedAndWorkstationWins() {
        assertEquals(Spot.MARKET, SpotMapper.spotFor(at(0, 0), Optional.of(at(2.5, 0)),
                Optional.of(at(0.5, 0)), Spot.MARKET));
        assertEquals(Spot.HOME, SpotMapper.spotFor(at(0, 0), Optional.of(at(0.5, 0)),
                Optional.of(at(2.5, 0)), Spot.MARKET));
    }

    @Test
    void aTieGoesToTheBedSoTheAnswerNeverDependsOnLookupOrder() {
        assertEquals(Spot.HOME, SpotMapper.spotFor(at(0, 0), Optional.of(at(1, 0)),
                Optional.of(at(-1, 0)), Spot.MARKET));
    }

    @Test
    void rangeIsMeasuredInThreeDimensions() {
        // Directly above their bed, far enough up to be out of range: upstairs, not in it.
        // Expressed against the range itself, so widening the range does not turn this
        // into a test that a villager two floors up is tucked in bed.
        double wellAbove = SpotMapper.AT_A_PLACE * 2;

        assertEquals(SpotMapper.ANYWHERE_ELSE, SpotMapper.spotFor(
                new SpotMapper.Place(0, 64 + wellAbove, 0),
                Optional.of(new SpotMapper.Place(0, 64, 0)), NO_JOB, Spot.MARKET));
        assertEquals(Spot.HOME, SpotMapper.spotFor(
                new SpotMapper.Place(0, 64 + SpotMapper.AT_A_PLACE / 2, 0),
                Optional.of(new SpotMapper.Place(0, 64, 0)), NO_JOB, Spot.MARKET),
                "and near enough above it still counts");
    }

    @Test
    void theRangeIsTheOneSweptAgainstARecordedVillage() {
        // E13: at three blocks villagers were in the market 5% of the time and it barely
        // opened; at ten, 44%, which is close to what the headless model assumes.
        assertEquals(10.0, SpotMapper.AT_A_PLACE,
                "changing this changes every in-game session; sweep it, do not nudge it");
    }
}
