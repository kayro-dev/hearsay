package hearsay;

import java.util.Collections;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * One villager. Mutable, and only ever mutated from {@link WorldState#apply(Event)} —
 * the same rule the world state itself follows.
 *
 * <p>Beliefs live in a TreeMap so iteration order is fixed by the claim, never by hash
 * order. Nothing fills it yet; rumors arrive in week 4.
 */
public final class Villager {
    private final int id;
    private final String name;
    private final Traits traits;
    private final NavigableMap<Claim, Belief> beliefs = new TreeMap<>();
    private Spot spot;

    Villager(int id, String name, Traits traits, Spot spot) {
        this.id = id;
        this.name = name;
        this.traits = traits;
        this.spot = spot;
    }

    void moveTo(Spot destination) {
        this.spot = destination;
    }

    public int id() { return id; }
    public String name() { return name; }
    public Traits traits() { return traits; }
    public Spot spot() { return spot; }
    public NavigableMap<Claim, Belief> beliefs() { return Collections.unmodifiableNavigableMap(beliefs); }

    @Override
    public boolean equals(Object o) {
        return o instanceof Villager v
            && id == v.id
            && name.equals(v.name)
            && traits.equals(v.traits)
            && spot == v.spot
            && beliefs.equals(v.beliefs);
    }

    @Override
    public int hashCode() { return Objects.hash(id, name, traits, spot, beliefs); }

    @Override
    public String toString() { return name + "#" + id + " at " + spot; }
}
