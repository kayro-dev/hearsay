package hearsay;

/**
 * One tick of the simulation is one part of a day. Coarse enough to stay readable,
 * fine enough for the village to have a rhythm. The Minecraft adapter will translate
 * real game time into these later.
 */
public enum DayPart {
    MORNING("morning"),
    MIDDAY("midday"),
    EVENING("evening"),
    NIGHT("night");

    private static final int PARTS_PER_DAY = 4;

    private final String description;

    DayPart(String description) {
        this.description = description;
    }

    public String description() { return description; }

    /** Tick 1 is the morning of day 1. */
    public static DayPart of(long tick) {
        return values()[(int) Math.floorMod(tick - 1, PARTS_PER_DAY)];
    }

    /** Day 1 is ticks 1-4. */
    public static long dayOf(long tick) {
        return Math.floorDiv(tick - 1, PARTS_PER_DAY) + 1;
    }

    /**
     * How strongly this part of the day pulls a villager toward a spot, as a relative
     * weight. The market is busy at midday; night sends nearly everyone home. These are
     * the main tuning knobs for how fast news travels, so they live in one place.
     *
     * <p>Both switches are exhaustive, so adding a Spot or a DayPart will not compile
     * until its weights are decided here.
     */
    int weight(Spot spot) {
        return switch (this) {
            case MORNING -> switch (spot) {
                case FIELDS -> 4;
                case WELL   -> 3;
                case MARKET -> 2;
                case HOME   -> 1;
            };
            case MIDDAY -> switch (spot) {
                case MARKET -> 5;
                case FIELDS -> 3;
                case WELL   -> 2;
                case HOME   -> 0;
            };
            case EVENING -> switch (spot) {
                case HOME   -> 4;
                case WELL   -> 3;
                case MARKET -> 2;
                case FIELDS -> 1;
            };
            case NIGHT -> switch (spot) {
                case HOME   -> 19;
                case WELL   -> 1;
                case MARKET -> 0;
                case FIELDS -> 0;
            };
        };
    }
}
