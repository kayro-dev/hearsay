package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class ProximityPairingTest {

    private static ProximityPairing.Position at(int id, double x, double z) {
        return new ProximityPairing.Position(id, x, 64, z);
    }

    /** A handful of villagers standing close enough to all talk to each other. */
    private static List<ProximityPairing.Position> aHuddle() {
        return List.of(at(0, 0, 0), at(1, 1, 0), at(2, 2, 0), at(3, 3, 0),
                at(4, 0, 1), at(5, 1, 1));
    }

    @Test
    void villagersStandingTogetherArePaired() {
        List<ProximityPairing.Encounter> pairs = ProximityPairing.pairsWithin(
                List.of(at(0, 0, 0), at(1, 2, 0)), 6, 1);

        assertEquals(List.of(new ProximityPairing.Encounter(0, 1)), pairs);
    }

    @Test
    void villagersTooFarApartAreNot() {
        assertEquals(List.of(), ProximityPairing.pairsWithin(
                List.of(at(0, 0, 0), at(1, 20, 0)), 6, 1));
    }

    @Test
    void rangeIsMeasuredInThreeDimensions() {
        List<ProximityPairing.Position> stacked = List.of(
                new ProximityPairing.Position(0, 0, 64, 0),
                new ProximityPairing.Position(1, 0, 74, 0));

        assertEquals(List.of(), ProximityPairing.pairsWithin(stacked, 6, 1));
    }

    @Test
    void nobodyIsPairedTwiceInOneTick() {
        for (long seed = 1; seed <= 20; seed++) {
            List<Integer> seen = new ArrayList<>();
            for (ProximityPairing.Encounter pair : ProximityPairing.pairsWithin(aHuddle(), 6, seed)) {
                seen.add(pair.a());
                seen.add(pair.b());
            }
            assertEquals(seen.size(), seen.stream().distinct().count(),
                    "somebody was paired twice on seed " + seed);
        }
    }

    @Test
    void anOddVillagerOutMeetsNobody() {
        assertEquals(1, ProximityPairing.pairsWithin(
                List.of(at(0, 0, 0), at(1, 1, 0), at(2, 2, 0)), 6, 1).size());
    }

    @Test
    void theAnswerDoesNotDependOnTheOrderPositionsArriveIn() {
        List<ProximityPairing.Position> village = new ArrayList<>(aHuddle());
        List<ProximityPairing.Encounter> expected = ProximityPairing.pairsWithin(village, 6, 7);

        for (int shuffle = 1; shuffle <= 5; shuffle++) {
            List<ProximityPairing.Position> jumbled = new ArrayList<>(village);
            Collections.rotate(jumbled, shuffle);
            assertEquals(expected, ProximityPairing.pairsWithin(jumbled, 6, 7),
                    "the pairing changed when the positions arrived in a different order");
        }
    }

    @Test
    void theSameSeedAlwaysGivesTheSamePairing() {
        assertEquals(ProximityPairing.pairsWithin(aHuddle(), 6, 42),
                ProximityPairing.pairsWithin(aHuddle(), 6, 42));
    }

    @Test
    void whoTalksToWhomRotatesAsTheSeedChanges() {
        // The whole point. With nearest-partner pairing a villager's partner never changed,
        // so a rumor was told to the same handful over and over while it decayed, and never
        // reached anybody new while it was still worth repeating.
        TreeSet<String> partnersOfZero = new TreeSet<>();
        for (long seed = 1; seed <= 40; seed++) {
            for (ProximityPairing.Encounter pair : ProximityPairing.pairsWithin(aHuddle(), 6, seed)) {
                if (pair.a() == 0) {
                    partnersOfZero.add(String.valueOf(pair.b()));
                } else if (pair.b() == 0) {
                    partnersOfZero.add(String.valueOf(pair.a()));
                }
            }
        }
        assertTrue(partnersOfZero.size() >= 3,
                "villager 0 only ever talked to " + partnersOfZero + " across 40 ticks");
    }

    @Test
    void aPartnerIsAlwaysSomebodyWithinRange() {
        // Shuffling decides who among the people near you, never who is far away.
        List<ProximityPairing.Position> spread = List.of(
                at(0, 0, 0), at(1, 2, 0), at(2, 4, 0), at(3, 100, 0), at(4, 102, 0));

        for (long seed = 1; seed <= 20; seed++) {
            for (ProximityPairing.Encounter pair : ProximityPairing.pairsWithin(spread, 6, seed)) {
                boolean bothNear = pair.a() <= 2 && pair.b() <= 2;
                boolean bothFar = pair.a() >= 3 && pair.b() >= 3;
                assertTrue(bothNear || bothFar,
                        "paired across 100 blocks on seed " + seed + ": " + pair);
            }
        }
    }

    @Test
    void anEncounterAlwaysNamesTheLowerIdFirst() {
        assertEquals(new ProximityPairing.Encounter(2, 9), new ProximityPairing.Encounter(9, 2));
    }
}
