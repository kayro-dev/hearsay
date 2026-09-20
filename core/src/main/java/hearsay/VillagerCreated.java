package hearsay;

/** A villager comes into existence. The log describes the whole world from nothing. */
public record VillagerCreated(long tick, int id, String name, Traits traits) implements Event {}
