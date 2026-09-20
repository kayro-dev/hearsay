package hearsay;

/**
 * A day is over. One event whose consequences are worked out deterministically in
 * {@link WorldState#apply(Event)}: every belief fades and the faintest are forgotten.
 * A fact can be a single event even when it touches everything, as long as what follows
 * from it involves no randomness.
 *
 * <p>The decay and the forgetting threshold are stored on the event rather than read from
 * the run's {@link Params}. The log records what happened, not how it was worked out, so
 * retuning the decay later cannot change what an old log replays to.
 *
 * @param decay            confidence is multiplied by this
 * @param forgetThreshold  a belief weaker than this is dropped entirely
 */
public record DayEnded(long tick, double decay, double forgetThreshold) implements Event {}
