package hearsay;

/**
 * An input event: someone outside the village put a rumor into a villager's head. This is
 * the event a counterfactual removes.
 */
public record RumorPlanted(long tick, int rumorId, Claim claim, int severity, int villagerId)
        implements Event {}
