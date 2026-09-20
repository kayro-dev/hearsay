package hearsay;

/**
 * Where one villager was standing at one tick, and how far they were from the two places
 * that decide what spot that makes them.
 *
 * <p>Not part of a run. A recipe records the spot that was decided; this records what it
 * was decided from, so the rule itself can be swept afterwards instead of guessed at. Every
 * other number in this project was chosen by sweeping, and the mapping from position to
 * spot is the one that could not be.
 *
 * @param toBed     distance to their bed, or {@link #NO_SUCH_PLACE}
 * @param toJobSite distance to their workstation, or {@link #NO_SUCH_PLACE}
 * @param jobSiteKind what sort of spot their workstation counts as, which is the only part
 *                    that needs to know one block from another
 */
public record Sighting(long tick, int villagerId, double x, double y, double z,
                       double toBed, double toJobSite, Spot jobSiteKind) {

    /** They have no bed, or no workstation, or it could not be read. */
    public static final double NO_SUCH_PLACE = -1;

    public boolean hasBed() { return toBed >= 0; }

    public boolean hasJobSite() { return toJobSite >= 0; }

    /**
     * What spot this sighting makes, under a given rule. The same shape as
     * {@link SpotMapper}, but from distances already measured, so a sweep can ask the
     * question again at a different range without a server.
     */
    public Spot spotWithin(double range) {
        boolean nearBed = hasBed() && toBed <= range;
        boolean nearWork = hasJobSite() && toJobSite <= range;
        if (!nearBed && !nearWork) {
            return SpotMapper.ANYWHERE_ELSE;
        }
        if (nearBed && nearWork) {
            return toBed <= toJobSite ? Spot.HOME : jobSiteKind;
        }
        return nearBed ? Spot.HOME : jobSiteKind;
    }
}
