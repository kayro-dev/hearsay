package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SurveyFileTest {

    private static final List<Sighting> A_FEW = List.of(
            new Sighting(1, 0, 10.5, 64, -3.25, 1.5, 12.0, Spot.MARKET),
            new Sighting(1, 1, 11.0, 64, -3.00, Sighting.NO_SUCH_PLACE, 2.0, Spot.FIELDS),
            new Sighting(2, 0, 10.5, 64, -3.25, 30.0, Sighting.NO_SUCH_PLACE, Spot.MARKET));

    @Test
    void aSurveySurvivesTheRoundTrip(@TempDir Path folder) {
        Path file = folder.resolve("survey.csv");

        SurveyFile.write(A_FEW, file);

        assertEquals(A_FEW, SurveyFile.read(file));
    }

    @Test
    void theSpotCanBeWorkedOutAgainAtADifferentRange() {
        Sighting nearBed = A_FEW.get(0); // 1.5 from bed, 12 from work

        assertEquals(Spot.HOME, nearBed.spotWithin(3));
        assertEquals(Spot.HOME, nearBed.spotWithin(20), "still nearer the bed than the work");
        assertEquals(SpotMapper.ANYWHERE_ELSE, nearBed.spotWithin(1),
                "too far from either to count as at one");
    }

    @Test
    void aWiderRangeCanBringAWorkstationIntoReach() {
        Sighting atWork = A_FEW.get(1); // no bed, 2 from a field

        assertEquals(SpotMapper.ANYWHERE_ELSE, atWork.spotWithin(1));
        assertEquals(Spot.FIELDS, atWork.spotWithin(3));
    }

    @Test
    void aMissingPlaceIsNeverNear() {
        Sighting bedless = A_FEW.get(1);
        Sighting jobless = A_FEW.get(2);

        assertFalse(bedless.hasBed());
        assertFalse(jobless.hasJobSite());
        assertEquals(SpotMapper.ANYWHERE_ELSE, jobless.spotWithin(5),
                "30 from the bed and no job at all");
        assertEquals(Spot.HOME, jobless.spotWithin(40), "a wide enough range reaches the bed");
    }

    @Test
    void theSurveyAgreesWithTheMapperItStandsIn() {
        // The same question asked two ways must give the same answer, or a sweep would be
        // tuning a rule the game does not use.
        for (double range : new double[] {1, 3, 6, 12, 30}) {
            for (Sighting sighting : A_FEW) {
                Spot fromSighting = sighting.spotWithin(range);
                Spot fromMapper = SpotMapper.spotFor(
                        new SpotMapper.Place(0, 0, 0),
                        sighting.hasBed()
                                ? java.util.Optional.of(new SpotMapper.Place(sighting.toBed(), 0, 0))
                                : java.util.Optional.empty(),
                        sighting.hasJobSite()
                                ? java.util.Optional.of(new SpotMapper.Place(sighting.toJobSite(), 0, 0))
                                : java.util.Optional.empty(),
                        sighting.jobSiteKind(), range);
                assertEquals(fromMapper, fromSighting,
                        "range " + range + ", villager " + sighting.villagerId());
            }
        }
    }

    @Test
    void somethingThatIsNotASurveyIsRefused(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("nope.csv");
        Files.writeString(file, "x,y,z\n1,2,3\n");

        assertThrows(IllegalArgumentException.class, () -> SurveyFile.read(file));
    }
}
