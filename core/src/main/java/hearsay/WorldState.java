package hearsay;

import java.util.Collections;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Everything the world is. The only place state changes is {@link #apply(Event)}, so a
 * fresh world replayed over the same event list is always identical to the original.
 *
 * <p>Needs no {@link Params}. Every event carries the numbers its own consequences depend
 * on, so a log replays to the same world whatever the knobs are set to today.
 */
public final class WorldState {

    private long tick = 0;

    /** Unset until the market first settles on a price. */
    private int marketPrice = 0;
    private boolean priceKnown = false;

    /** Keyed by id, so iteration order is id order rather than hash order. */
    private final NavigableMap<Integer, Villager> villagers = new TreeMap<>();
    private final NavigableMap<Integer, Rumor> rumors = new TreeMap<>();

    /** Rumor ids come from here, never from randomness. */
    private int nextRumorId = 0;

    public void apply(Event event) {
        switch (event) {
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
                addRumor(new Rumor(e.rumorId(), e.claim(), e.severity(), Rumor.NO_PARENT,
                        e.tick(), RumorOrigin.PLANTED));
                villager(e.villagerId()).believe(
                        Belief.planted(e.claim(), e.confidence(), e.tick(), e.rumorId()));
            }
            case RumorMutated e -> {
                tick = e.tick();
                Rumor parent = rumor(e.parentId());
                addRumor(new Rumor(e.rumorId(), parent.claim(), e.severity(), e.parentId(),
                        e.tick(), parent.origin()));
            }
            case RumorTold e -> {
                tick = e.tick();
                Rumor kept = rumor(e.keptRumorId());
                villager(e.listenerId()).believe(new Belief(kept.claim(), e.newConfidence(),
                        e.tellerId(), e.tick(), e.keptRumorId(), chainAfter(e, kept.claim())));
            }
            case MarketPriceSet e -> {
                tick = e.tick();
                marketPrice = e.price();
                priceKnown = true;
            }
            case PriceObserved e -> {
                tick = e.tick();
                // The first villager to read this conclusion out of the price starts a
                // rumor family for it; everyone after joins the family already there.
                if (!rumors.containsKey(e.rumorId())) {
                    addRumor(new Rumor(e.rumorId(), e.claim(), Rumor.MIN_SEVERITY,
                            Rumor.NO_PARENT, e.tick(), RumorOrigin.OBSERVED));
                }
                Rumor seen = rumor(e.rumorId());
                Belief held = villager(e.villagerId()).belief(seen.claim());
                NavigableSet<Integer> chain = new TreeSet<>();
                if (held != null) {
                    chain.addAll(held.chain());
                }
                chain.add(Belief.MARKET);
                villager(e.villagerId()).believe(new Belief(seen.claim(), e.newConfidence(),
                        Belief.MARKET, e.tick(), e.rumorId(), chain));
            }
            case DayEnded e -> {
                tick = e.tick();
                for (Villager villager : villagers.values()) { // id order
                    villager.fade(e.decay(), e.forgetThreshold());
                }
            }
        }
    }

    /**
     * The history the listener's belief now carries: everywhere the teller's belief had
     * been, plus the teller. The listener is dropped, so nobody sits in their own chain.
     */
    private NavigableSet<Integer> chainAfter(RumorTold told, Claim claim) {
        NavigableSet<Integer> chain = new TreeSet<>();
        Belief tellerBelief = villager(told.tellerId()).belief(claim);
        if (tellerBelief != null) {
            chain.addAll(tellerBelief.chain());
        }
        chain.add(told.tellerId());
        chain.remove(told.listenerId());
        return chain;
    }

    private void addRumor(Rumor rumor) {
        rumors.put(rumor.id(), rumor);
        nextRumorId = Math.max(nextRumorId, rumor.id() + 1);
    }

    public long tick() { return tick; }

    /** The last price the market settled on, empty until it first does. */
    public OptionalInt marketPrice() {
        return priceKnown ? OptionalInt.of(marketPrice) : OptionalInt.empty();
    }
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

    /** Walks a rumor's parents back to the rumor its family started with. */
    public Rumor rootOf(Rumor rumor) {
        Rumor current = rumor;
        while (!current.isRoot()) {
            current = rumor(current.parentId());
        }
        return current;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof WorldState w
            && tick == w.tick
            && marketPrice == w.marketPrice
            && priceKnown == w.priceKnown
            && nextRumorId == w.nextRumorId
            && villagers.equals(w.villagers)
            && rumors.equals(w.rumors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tick, marketPrice, priceKnown, nextRumorId, villagers, rumors);
    }

    @Override
    public String toString() {
        return "tick=" + tick + ", price=" + (priceKnown ? marketPrice : "unset")
                + ", villagers=" + villagers.size() + ", rumors=" + rumors.size();
    }
}
