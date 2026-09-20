package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProximityPairingTest {

    private static ProximityPairing.Position at(int id, double x, double z) {
        return new ProximityPairing.Position(id, x, 64, z);
    }

    @Test
    void villagersStandingTogetherArePaired() {
        List<ProximityPairing.Encounter> pairs = ProximityPairing.pairsWithin(
                List.of(at(0, 0, 0), at(1, 2, 0)), 6);

        assertEquals(List.of(new ProximityPairing.Encounter(0, 1)), pairs);
    }

    @Test
    void villagersTooFarApartAreNot() {
        assertEquals(List.of(), ProximityPairing.pairsWithin(
                List.of(at(0, 0, 0), at(1, 20, 0)), 6));
    }

    @Test
    void rangeIsMeasuredInThreeDimensions() {
        // Ten blocks straight up is out of range even though they share a footprint.
        List<ProximityPairing.Position> stacked = List.of(
                new ProximityPairing.Position(0, 0, 64, 0),
                new ProximityPairing.Position(1, 0, 74, 0));

        assertEquals(List.of(), ProximityPairing.pairsWithin(stacked, 6));
    }

    @Test
    void nobodyIsPairedTwiceInOneTick() {
        // Four villagers in a huddle, all within range of each other.
        List<ProximityPairing.Encounter> pairs = ProximityPairing.pairsWithin(
                List.of(at(0, 0, 0), at(1, 1, 0), at(2, 2, 0), at(3, 3, 0)), 6);

        List<Integer> seen = new ArrayList<>();
        for (ProximityPairing.Encounter pair : pairs) {
            seen.add(pair.a());
            seen.add(pair.b());
        }
        assertEquals(seen.size(), seen.stream().distinct().count(),
                "somebody was paired twice: " + pairs);
        assertEquals(2, pairs.size());
    }

    @Test
    void anOddVillagerOutMeetsNobody() {
        List<ProximityPairing.Encounter> pairs = ProximityPairing.pairsWithin(
                List.of(at(0, 0, 0), at(1, 1, 0), at(2, 2, 0)), 6);

        assertEquals(1, pairs.size());
    }

    @Test
    void theAnswerDoesNotDependOnTheOrderPositionsArriveIn() {
        List<ProximityPairing.Position> village = new ArrayList<>(List.of(
                at(3, 8, 1), at(7, 0, 0), at(1, 1, 1), at(9, 9, 0), at(5, 30, 30)));
        List<ProximityPairing.Encounter> expected = ProximityPairing.pairsWithin(village, 6);

        for (int shuffle = 0; shuffle < 5; shuffle++) {
            List<ProximityPairing.Position> jumbled = new ArrayList<>(village);
            Collections.rotate(jumbled, shuffle + 1);
            assertEquals(expected, ProximityPairing.pairsWithin(jumbled, 6),
                    "the pairing changed when the positions arrived in a different order");
        }
        assertFalse(expected.isEmpty());
    }

    @Test
    void theLowerIdTakesTheNearerPartner() {
        // 0 is closer to 1 than to 2, so 0 pairs with 1 and 2 is left over.
        List<ProximityPairing.Encounter> pairs = ProximityPairing.pairsWithin(
                List.of(at(0, 0, 0), at(1, 1, 0), at(2, 5, 0)), 6);

        assertEquals(List.of(new ProximityPairing.Encounter(0, 1)), pairs);
    }

    @Test
    void anEncounterAlwaysNamesTheLowerIdFirst() {
        assertEquals(new ProximityPairing.Encounter(2, 9), new ProximityPairing.Encounter(9, 2));
    }
}
