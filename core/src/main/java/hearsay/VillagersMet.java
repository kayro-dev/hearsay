package hearsay;

/**
 * Two villagers run into each other at a spot. Carries no state change yet; this is the
 * hook gossip hangs off in week 4.
 */
public record VillagersMet(long tick, int a, int b, Spot spot) implements Event {}
