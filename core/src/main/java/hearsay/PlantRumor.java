package hearsay;

/** Plant a rumor in one villager's head at a given tick. Carries no id: the simulation
 * assigns that from its counter when the tick arrives. */
public record PlantRumor(long tick, Claim claim, int severity, int villagerId) implements Input {}
