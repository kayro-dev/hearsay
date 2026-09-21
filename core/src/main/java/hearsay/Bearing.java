package hearsay;

/**
 * Which way to walk, in the words a player already uses for directions.
 *
 * <p>Here rather than in the plugin for the same reason {@link ProximityPairing} is: it is
 * arithmetic with edge cases and no Minecraft in it, so it can be tested without a server.
 */
public final class Bearing {

    /**
     * How far apart two things must be along an axis before that axis is worth naming. Below
     * it, "north-east" would send a player walking for what is already in front of them.
     */
    static final double CLOSE_ENOUGH = 4.0;

    private Bearing() {
    }

    /**
     * @param east  how far east the target is, negative for west
     * @param south how far south the target is, negative for north
     * @return a compass direction, or "right here" when it is neither
     */
    public static String of(double east, double south) {
        boolean northSouth = Math.abs(south) > CLOSE_ENOUGH;
        boolean eastWest = Math.abs(east) > CLOSE_ENOUGH;
        if (!northSouth && !eastWest) {
            return "right here";
        }
        StringBuilder bearing = new StringBuilder();
        if (northSouth) {
            bearing.append(south < 0 ? "north" : "south");
        }
        if (eastWest) {
            bearing.append(northSouth ? "-" : "").append(east > 0 ? "east" : "west");
        }
        return bearing.toString();
    }
}
