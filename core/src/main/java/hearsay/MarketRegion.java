package hearsay;

/**
 * A patch of ground that counts as the market, whatever the villagers standing in it do
 * for a living.
 *
 * <p>Until this existed, who was in the market was inferred from workstations, and E13 to
 * E19 were all downstream of that inference being wrong: a villager at a stall was a
 * "market" villager and one browsing three blocks away was not. A marked region answers the
 * question by fiat, which is both more honest and easier to explain to a player, who can
 * see where the market is.
 *
 * <p>Not simulation state. The region decides which sightings the plugin reports, and the
 * sightings are what get recorded, so a recipe reproduces the run without needing to know
 * the region ever existed.
 *
 * <p>Height is deliberately included. A villager in a cellar under the market square is not
 * in the market, and a flat circle would say they were.
 *
 * @param radius how far from the centre, in blocks
 */
public record MarketRegion(double x, double y, double z, double radius) {

    /** Small enough to be a mistake: a market nobody can stand in two of. */
    public static final double SMALLEST = 2.0;

    /** Large enough to be a mistake: a market the size of a village is not a market. */
    public static final double LARGEST = 64.0;

    public MarketRegion {
        if (!(radius >= SMALLEST && radius <= LARGEST)) {
            throw new IllegalArgumentException(
                    "A market radius must be " + SMALLEST + " to " + LARGEST
                            + " blocks, was " + radius);
        }
    }

    /** True if something standing here is in the market. */
    public boolean contains(double px, double py, double pz) {
        double dx = px - x;
        double dy = py - y;
        double dz = pz - z;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    /** How many blocks across, for telling the player what they just made. */
    public int diameter() {
        return (int) Math.round(radius * 2);
    }
}
