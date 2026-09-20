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
        // Directly above their bed by more than the range: upstairs, not in it.
        assertEquals(SpotMapper.ANYWHERE_ELSE, SpotMapper.spotFor(
                new SpotMapper.Place(0, 70, 0), Optional.of(new SpotMapper.Place(0, 64, 0)),
                NO_JOB, Spot.MARKET));
    }
}
