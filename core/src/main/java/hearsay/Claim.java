package hearsay;

/**
 * A structured statement about the world, e.g. ("diamond", SCARCE). Structured rather
 * than free text so claims can be compared, counted and contradicted.
 *
 * <p>Comparable so villagers can keep their beliefs in a TreeMap: the simulation must
 * never iterate in hash order.
 */
public record Claim(String item, ClaimType type) implements Comparable<Claim> {

    @Override
    public int compareTo(Claim other) {
        int byItem = item.compareTo(other.item);
        return byItem != 0 ? byItem : type.compareTo(other.type);
    }
}
