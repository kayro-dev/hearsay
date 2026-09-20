package hearsay;

/** A place villagers gather. Meetings only happen between villagers at the same spot. */
public enum Spot {
    WELL("the well"),
    MARKET("the market"),
    FIELDS("the fields"),
    HOME("home");

    private final String description;

    Spot(String description) {
        this.description = description;
    }

    /** How the narrator refers to this spot, e.g. "the well". */
    public String description() { return description; }
}
