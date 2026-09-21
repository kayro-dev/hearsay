package hearsay;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.OptionalInt;
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
    private final int neighbourhood;
    private final NavigableMap<Claim, Belief> beliefs = new TreeMap<>();
    private Spot spot;

    /** The price this villager last read something into, or 0 if they never have. */
    private int lastObservedPrice = 0;

    /** When they were last known to be at the market, or {@link #NEVER}. */
    private long lastAtMarket = NEVER;

    private static final long NEVER = Long.MIN_VALUE;

    Villager(int id, String name, Traits traits, Spot spot, int neighbourhood) {
        this.id = id;
        this.name = name;
        this.traits = traits;
        this.spot = spot;
        this.neighbourhood = neighbourhood;
    }

    void moveTo(Spot destination, long tick) {
        this.spot = destination;
        if (destination == Spot.MARKET) {
            lastAtMarket = tick;
        }
    }

    /**
     * Whether this villager counts as being in the market, allowing for a market that is
     * watched rather than photographed: somebody who was at a stall a moment ago and is
     * walking back to it is still a trader.
     *
     * @param within how many ticks of grace a villager keeps after leaving
     */
    public boolean inTheMarket(long tick, int within) {
        if (spot == Spot.MARKET) {
            return true;
        }
        // Guarded rather than arithmetic: subtracting NEVER from a tick overflows, and a
        // negative answer compares as "recently", which would put every villager who has
        // never set foot in the market permanently inside it.
        return lastAtMarket != NEVER && tick - lastAtMarket <= within;
    }

    void sawPrice(int price) {
        this.lastObservedPrice = price;
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

    /** Which part of the village this villager lives and works in. */
    public int neighbourhood() { return neighbourhood; }
    public Spot spot() { return spot; }

    /**
     * The price this villager last drew a conclusion from, or empty if they never have.
     * Evidence comes from how far the price has moved since then, not from where it
     * stands, so a price that stops climbing stops being news.
     */
    public OptionalInt lastObservedPrice() {
        return lastObservedPrice == 0 ? OptionalInt.empty() : OptionalInt.of(lastObservedPrice);
    }

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
            && neighbourhood == v.neighbourhood
            && spot == v.spot
            && lastObservedPrice == v.lastObservedPrice
            && lastAtMarket == v.lastAtMarket
            && beliefs.equals(v.beliefs);
    }

    @Override
    public int hashCode() { return Objects.hash(id, name, traits, neighbourhood, spot, lastObservedPrice, lastAtMarket, beliefs); }

    @Override
    public String toString() { return name + "#" + id + " at " + spot; }
}
