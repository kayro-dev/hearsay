package hearsay;

/** A villager goes to a spot for this part of the day. */
public record VillagerMoved(long tick, int id, Spot spot) implements Event {}
