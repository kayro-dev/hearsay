package hearsay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Turns positions into pairs who are close enough to talk.
 *
 * <p>Plain arithmetic over coordinates, with nothing from any game in it, so the rule that
 * decides who can gossip with whom can be tested on its own rather than only by standing in
 * a village and watching. An adapter reads positions from wherever it gets them and hands
 * them here.
 *
 * <p>Pairing is greedy in villager order: the lowest id takes the nearest partner still
 * free, then the next, and so on. Ties go to the lower id. That makes the result depend only
 * on the positions given, never on the order they arrive in or on anything to do with a
 * hash, which is what lets an in-game run be replayed from its inputs.
 */
public final class ProximityPairing {

    private ProximityPairing() {
    }

    /** Where one villager is standing. */
    public record Position(int villagerId, double x, double y, double z) {

        double distanceTo(Position other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /** Two villagers near enough to talk, lower id first. */
    public record Encounter(int a, int b) {

        public Encounter {
            if (a == b) {
                throw new IllegalArgumentException("A villager cannot meet themselves: " + a);
            }
            if (a > b) {
                int swap = a;
                a = b;
                b = swap;
            }
        }
    }

    /**
     * Who is standing close enough to whom, with nobody paired twice.
     *
     * @param range how far apart two villagers may be and still be talking
     */
    public static List<Encounter> pairsWithin(List<Position> positions, double range) {
        List<Position> inOrder = new ArrayList<>(positions);
        inOrder.sort(Comparator.comparingInt(Position::villagerId));

        TreeSet<Integer> alreadyPaired = new TreeSet<>();
        List<Encounter> encounters = new ArrayList<>();

        for (Position one : inOrder) {
            if (alreadyPaired.contains(one.villagerId())) {
                continue;
            }
            Position nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (Position other : inOrder) {
                if (other.villagerId() == one.villagerId()
                        || alreadyPaired.contains(other.villagerId())) {
                    continue;
                }
                double distance = one.distanceTo(other);
                // Strictly nearer, so an equal distance leaves the earlier id in place.
                if (distance <= range && distance < nearestDistance) {
                    nearest = other;
                    nearestDistance = distance;
                }
            }
            if (nearest != null) {
                alreadyPaired.add(one.villagerId());
                alreadyPaired.add(nearest.villagerId());
                encounters.add(new Encounter(one.villagerId(), nearest.villagerId()));
            }
        }
        return encounters;
    }
}
