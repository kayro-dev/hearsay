package hearsay;

/**
 * A day is over. One event whose consequences are worked out deterministically in
 * {@link WorldState#apply(Event)}: every belief fades and the faintest are forgotten.
 * A fact can be a single event even when it touches everything, as long as what follows
 * from it involves no randomness.
 */
public record DayEnded(long tick) implements Event {}
