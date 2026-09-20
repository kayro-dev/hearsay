package hearsay;

import java.util.Collections;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Everything the world is. The only place state changes is {@link #apply(Event)}, so a
 * fresh world replayed over the same event list is always identical to the original.
 *
 * <p>Holds the run's {@link Params} because one event, {@link DayEnded}, has consequences
 * that are calculated rather than stored. Replaying a log therefore needs the params it
 * was produced with, which is why the recipe for a run is seed + params + inputs.
 */
public final class WorldState {

    private final Params params;

    private long tick = 0;
    private int diamondPrice = 100;

    /** Keyed by id, so iteration order is id order rather than hash order. */
    private final NavigableMap<Integer, Villager> villagers = new TreeMap<>();
    private final NavigableMap<Integer, Rumor> rumors = new TreeMap<>();

    /** Rumor ids come from here, never from randomness. */
    private int nextRumorId = 0;

    public WorldState(Params params) {
        this.params = params;
    }

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
                // Meeting alone changes nothing. What gets said is its own event.
                tick = e.tick();
            }
            case RumorPlanted e -> {
                tick = e.tick();
                addRumor(new Rumor(e.rumorId(), e.claim(), e.severity(), Rumor.NO_PARENT, e.tick()));
                villager(e.villagerId()).believe(new Belief(
                        e.claim(), params.plantedConfidence(), Belief.NO_SOURCE, e.tick(), e.rumorId()));
            }
            case RumorMutated e -> {
                tick = e.tick();
                Rumor parent = rumor(e.parentId());
                addRumor(new Rumor(e.rumorId(), parent.claim(), e.severity(), e.parentId(), e.tick()));
            }
            case RumorTold e -> {
                tick = e.tick();
                Rumor rumor = rumor(e.rumorId());
                villager(e.listenerId()).believe(new Belief(
                        rumor.claim(), e.newConfidence(), e.tellerId(), e.tick(), e.rumorId()));
            }
            case DayEnded e -> {
                tick = e.tick();
                for (Villager villager : villagers.values()) { // id order
                    villager.fade(e.decay(), e.forgetThreshold());
                }
            }
        }
    }

    private void addRumor(Rumor rumor) {
        rumors.put(rumor.id(), rumor);
        nextRumorId = Math.max(nextRumorId, rumor.id() + 1);
    }

    public Params params() { return params; }
    public long tick() { return tick; }
    public int diamondPrice() { return diamondPrice; }
    public int nextRumorId() { return nextRumorId; }

    public NavigableMap<Integer, Villager> villagers() {
        return Collections.unmodifiableNavigableMap(villagers);
    }

    public NavigableMap<Integer, Rumor> rumors() {
        return Collections.unmodifiableNavigableMap(rumors);
    }

    public Villager villager(int id) {
        Villager villager = villagers.get(id);
        if (villager == null) {
            throw new IllegalArgumentException("No villager with id " + id);
        }
        return villager;
    }

    public Rumor rumor(int id) {
        Rumor rumor = rumors.get(id);
        if (rumor == null) {
            throw new IllegalArgumentException("No rumor with id " + id);
        }
        return rumor;
    }

    /** Walks a rumor's parents back to the planted rumor it descends from. */
    public Rumor rootOf(Rumor rumor) {
        Rumor current = rumor;
        while (!current.isPlanted()) {
            current = rumor(current.parentId());
        }
        return current;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof WorldState w
            && tick == w.tick
            && diamondPrice == w.diamondPrice
            && nextRumorId == w.nextRumorId
            && villagers.equals(w.villagers)
            && rumors.equals(w.rumors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tick, diamondPrice, nextRumorId, villagers, rumors);
    }

    @Override
    public String toString() {
        return "tick=" + tick + ", diamondPrice=" + diamondPrice
                + ", villagers=" + villagers.size() + ", rumors=" + rumors.size();
    }
}
