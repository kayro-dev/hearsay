package hearsay;

/**
 * Something outside saw this villager standing somewhere.
 *
 * <p>Without it, where a villager is can only be inferred from who they were last seen
 * talking to, which means a villager standing at a market stall on their own is invisible.
 * A village of thirty-one showed at most eight in its market at any moment for that reason
 * alone, and its market opened on 1% of ticks.
 *
 * <p>Reported for every villager every tick, but only written into the log when it changes
 * something: a villager who has not moved has told the world nothing new.
 */
public record VillagerSeen(long tick, int villagerId, Spot spot) implements Input {}
