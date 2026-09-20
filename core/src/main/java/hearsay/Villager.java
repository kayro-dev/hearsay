package hearsay;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * One villager. Mutable, and only ever mutated from {@link WorldState#apply(Event)} —
 * the same rule the world state itself follows.
 *
 * <p>Beliefs live in a TreeMap so iteration order is fixed by the claim, never by hash
 * order, and at most one belief is held per claim.
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

    /** Replaces whatever was held about this claim. */
    void believe(Belief belief) {
        beliefs.put(belief.claim(), belief);
    }

    /** Everything fades a little, and the faintest beliefs go entirely. No randomness. */
    void fade(double decay, double forgetThreshold) {
        Iterator<Map.Entry<Claim, Belief>> beliefsHeld = beliefs.entrySet().iterator();
        while (beliefsHeld.hasNext()) {
            Map.Entry<Claim, Belief> entry = beliefsHeld.next();
            double faded = entry.getValue().confidence() * decay;
            if (faded < forgetThreshold) {
                beliefsHeld.remove();
            } else {
                entry.setValue(entry.getValue().withConfidence(faded));
            }
        }
    }

    public int id() { return id; }
    public String name() { return name; }
    public Traits traits() { return traits; }
    public Spot spot() { return spot; }

    public NavigableMap<Claim, Belief> beliefs() {
        return Collections.unmodifiableNavigableMap(beliefs);
    }

    /** What this villager holds about a claim, or null if they have never heard it. */
    public Belief belief(Claim claim) {
        return beliefs.get(claim);
    }

    /**
     * The belief this villager would bring up, meaning the strongest one worth mentioning.
     * Ties go to the first claim in claim order, never to whatever a hash happened to put
     * first. Returns null if nothing reaches the threshold.
     */
    public Belief strongestBeliefWorthTelling(double threshold) {
        Belief strongest = null;
        for (Belief belief : beliefs.values()) { // claim order
            if (belief.confidence() >= threshold
                    && (strongest == null || belief.confidence() > strongest.confidence())) {
                strongest = belief;
            }
        }
        return strongest;
    }

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
