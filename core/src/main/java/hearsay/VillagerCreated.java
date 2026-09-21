package hearsay;

/**
 * A villager comes into existence. The log describes the whole world from nothing.
 *
 * @param neighbourhood which part of the village they live and work in. Carried on the
 *                      event rather than recomputed, so a log replays to the same village
 *                      however the neighbourhood rule is later changed
 */
public record VillagerCreated(long tick, int id, String name, Traits traits,
                              int neighbourhood) implements Event {}
