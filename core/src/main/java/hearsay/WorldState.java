package hearsay;

import java.util.Collections;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Everything the world is. The only place state changes is {@link #apply(Event)}, so a
 * fresh world replayed over the same event list is always identical to the original.
 */
public final class WorldState {
    private long tick = 0;
    private int diamondPrice = 100;

    /** Keyed by villager id, so iteration order is id order rather than hash order. */
    private final NavigableMap<Integer, Villager> villagers = new TreeMap<>();

    public void apply(Event event) {
        switch (event) {
            case PriceChanged e -> {
                tick = e.tick();
                diamondPrice = Math.max(1, diamondPrice + e.delta());
            }
            case VillagerCreated e -> {
                tick = e.tick();
                villagers.put(e.id(), new Villager(e.id(), e.name(), e.traits(), Spot.HOME));
            }
            case VillagerMoved e -> {
                tick = e.tick();
                villager(e.id()).moveTo(e.spot());
            }
            case VillagersMet e -> {
                // Nothing changes hands yet. Week 4 hangs gossip off this event.
                tick = e.tick();
            }
        }
    }

    public long tick() { return tick; }
    public int diamondPrice() { return diamondPrice; }

    public NavigableMap<Integer, Villager> villagers() {
        return Collections.unmodifiableNavigableMap(villagers);
    }

    public Villager villager(int id) {
        Villager villager = villagers.get(id);
        if (villager == null) {
            throw new IllegalArgumentException("No villager with id " + id);
        }
        return villager;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof WorldState w
            && tick == w.tick
            && diamondPrice == w.diamondPrice
            && villagers.equals(w.villagers);
    }

    @Override
    public int hashCode() { return Objects.hash(tick, diamondPrice, villagers); }

    @Override
    public String toString() {
        return "tick=" + tick + ", diamondPrice=" + diamondPrice + ", villagers=" + villagers.size();
    }
}
