package hearsay;

/** Where a rumor family started. Only meaningful for a rumor with no parent. */
public enum RumorOrigin {
    /** Put into someone's head from outside the village. */
    PLANTED,
    /** Nobody said it: a villager worked it out from the price at the market. */
    OBSERVED
}
