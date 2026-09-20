package hearsay;

/**
 * An input event: someone outside the village put a rumor into a villager's head. This is
 * the event a counterfactual removes.
 *
 * <p>The confidence the villager starts with is stored on the event rather than read from
 * the run's {@link Params}, so retuning that knob cannot change what an old log replays
 * to. With this, nothing in {@link WorldState#apply(Event)} consults the params at all.
 */
public record RumorPlanted(long tick, int rumorId, Claim claim, int severity,
                           int villagerId, double confidence) implements Event {}
