package hearsay;

/**
 * A bubble: the price ran up past {@link #PEAK_ABOVE} and came back under
 * {@link #BACK_BELOW} before the run ended.
 *
 * <p>The definition lives here and nowhere else, so every command, experiment and test is
 * counting the same thing. A price that runs up and stays up is not a bubble but a change
 * of regime, and the coming back down is what tells the two apart.
 *
 * <p>Two other price measures exist and are deliberately named differently, because
 * confusing them with this one overstates the case: {@link #ELEVATED} is simply a price
 * above the ordinary, used to describe how long a run stayed dear, and a peak price says
 * how far it went without saying whether it ever came back.
 *
 * @param peakTick     when the price was at its highest
 * @param recoveryTick the first tick after the peak back under {@link #BACK_BELOW}
 * @param days         how long the fall took
 */
public record Bubble(int peakPrice, long peakTick, int recoveryPrice, long recoveryTick,
                     double days) {

    /** The price a run must exceed for what follows to count as a bubble. */
    public static final int PEAK_ABOVE = 130;

    /** ...and the price it must come back under. */
    public static final int BACK_BELOW = 110;

    /**
     * A price above the ordinary. Not a bubble on its own: a run can sit here for its whole
     * length without ever bubbling, and calling that a bubble would be the overstatement
     * this constant exists to avoid.
     */
    public static final int ELEVATED = 120;
}
