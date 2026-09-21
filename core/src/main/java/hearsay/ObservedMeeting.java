package hearsay;

/**
 * Something outside saw these two standing together. The input that replaces simulated
 * movement when {@link MeetingSource#EXTERNAL} is in force.
 *
 * <p>It carries a spot as well as a pair, because a pair on its own says who can talk but
 * not where anyone is, and the market is the average ask of whoever is standing in it. The
 * adapter decides which spot a location counts as.
 *
 * <p>One consequence worth knowing: a villager standing alone at the market is invisible to
 * the price, because only pairs are reported. Reporting positions separately would fix
 * that; for now the market is made of villagers seen meeting in it.
 */
public record ObservedMeeting(long tick, int a, int b, Spot spot) implements Input {

    public ObservedMeeting {
        if (a == b) {
            throw new IllegalArgumentException("A villager cannot meet themselves: " + a);
        }
    }
}
