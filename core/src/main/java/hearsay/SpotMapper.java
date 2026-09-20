package hearsay;

import java.util.Optional;

/**
 * Where a villager counts as standing, worked out from what the world already knows about
 * them: their bed and their workstation.
 *
 * <p>Plain arithmetic over coordinates, with nothing from any game in it, so the rule can
 * be tested rather than only squinted at in a village. The adapter looks up the two places
 * and says what kind of spot the workstation is; everything else is decided here.
 *
 * <p>Minecraft villagers already gather at workstations and go home to beds, so a spot
 * taken from those reflects what a viewer can see, rather than a radius somebody invented.
 */
public final class SpotMapper {

    /** How near a villager must be to their bed or workstation to count as being there. */
    public static final double AT_A_PLACE = 3.0;

    /**
     * Where a villager ends up when nothing else fits: jobless, bedless, or simply standing
     * somewhere else. The village at large, which is where most wandering happens.
     */
    public static final Spot ANYWHERE_ELSE = Spot.WELL;

    private SpotMapper() {
    }

    /** A point in the world. */
    public record Place(double x, double y, double z) {

        double distanceTo(Place other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /**
     * Where this villager counts as being.
     *
     * <p>Whichever of their bed or their workstation they are nearest to, if they are near
     * either at all. A villager with no job, no bed, or one who has wandered away from both
     * is at {@link #ANYWHERE_ELSE}: the rule never fails to give an answer, because a
     * villager is always somewhere.
     *
     * @param bed          where they sleep, if they have a bed
     * @param jobSite      where they work, if they have a workstation
     * @param jobSiteSpot  what kind of spot that workstation is, decided by the adapter,
     *                     which is the only part that needs to know one block from another
     */
    public static Spot spotFor(Place villager, Optional<Place> bed,
                               Optional<Place> jobSite, Spot jobSiteSpot) {
        return spotFor(villager, bed, jobSite, jobSiteSpot, AT_A_PLACE);
    }

    public static Spot spotFor(Place villager, Optional<Place> bed,
                               Optional<Place> jobSite, Spot jobSiteSpot, double range) {
        double toBed = bed.map(villager::distanceTo).orElse(Double.MAX_VALUE);
        double toWork = jobSite.map(villager::distanceTo).orElse(Double.MAX_VALUE);

        if (toBed > range && toWork > range) {
            return ANYWHERE_ELSE;
        }
        // Nearest wins, and a tie goes to the bed, so the answer never depends on which
        // was looked up first.
        return toBed <= toWork ? Spot.HOME : jobSiteSpot;
    }
}
