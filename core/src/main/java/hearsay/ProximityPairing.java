package hearsay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

/**
 * Turns positions into pairs who are close enough to talk.
 *
 * <p>Plain arithmetic over coordinates, with nothing from any game in it, so the rule that
 * decides who can gossip with whom can be tested on its own rather than only by standing in
 * a village and watching. An adapter reads positions from wherever it gets them and hands
 * them here.
 *
 * <p>Who talks to whom among the people standing together is decided by shuffling them,
 * not by taking the nearest. Nearest sounds like the careful choice and is the wrong one: a
 * villager's nearest neighbour is the same villager tick after tick, so a rumor is told to
 * the same handful over and over while its teller's confidence decays, and it never reaches
 * anybody new while it is still worth repeating. One played session had its busiest pairs
 * meet 126 and 119 times and produced 8 second-hand tellings where the model produced 181.
 * Shuffling is what the headless model has always done.
 *
 * <p>The shuffle is seeded by the caller, so the answer depends only on the positions and
 * the seed, never on the order the positions arrive in. An in-game run records what it
 * decided as inputs, so replay and counterfactuals are unaffected either way.
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
     * @param seed  decides who talks to whom among those in range. Give it something that
     *              changes every tick, or the same people will pair every time and the
     *              shuffle will have bought nothing
     */
    public static List<Encounter> pairsWithin(List<Position> positions, double range, long seed) {
        // Sorted before shuffling, so the same positions give the same answer whatever
        // order they were handed over in.
        List<Position> inOrder = new ArrayList<>(positions);
        inOrder.sort(Comparator.comparingInt(Position::villagerId));
        Collections.shuffle(inOrder, new Random(seed));

        TreeSet<Integer> alreadyPaired = new TreeSet<>();
        List<Encounter> encounters = new ArrayList<>();

        for (Position one : inOrder) {
            if (alreadyPaired.contains(one.villagerId())) {
                continue;
            }
            for (Position other : inOrder) {
                if (other.villagerId() == one.villagerId()
                        || alreadyPaired.contains(other.villagerId())
                        || one.distanceTo(other) > range) {
                    continue;
                }
                alreadyPaired.add(one.villagerId());
                alreadyPaired.add(other.villagerId());
                encounters.add(new Encounter(one.villagerId(), other.villagerId()));
                break;
            }
        }
        return encounters;
    }
}
