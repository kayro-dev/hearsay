package hearsay;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
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

    /**
     * The last price each good's market settled on. A good is absent until its market has
     * first settled, which is how "no price yet" is told from a price.
     */
    private final Map<Good, Integer> marketPrice = new EnumMap<>(Good.class);

    /**
     * Every price each market has settled on, by tick, so a villager can read the price
     * against its own recent trend (E46). In the world rather than in the simulation,
     * because a decision reads it: a forked or resumed world that lost it would read a
     * different trend and decide differently.
     */
    private final Map<Good, java.util.NavigableMap<Long, Integer>> priceHistory =
            new EnumMap<>(Good.class);

    /** How far each good's wobble currently stands from nothing. */
    private final Map<Good, Double> marketNoiseLevel = new EnumMap<>(Good.class);

    /** Keyed by id, so iteration order is id order rather than hash order. */
    private final NavigableMap<Integer, Villager> villagers = new TreeMap<>();
    private final NavigableMap<Integer, Rumor> rumors = new TreeMap<>();

    /**
     * Rumour ids come from here, never from randomness, and each good counts in its own
     * range. A single counter would let a gold rumour take id 3 and push diamond's next one
     * to 4, renumbering a diamond log that had nothing to do with gold.
     */
    private final Map<Good, Integer> nextRumorId = new EnumMap<>(Good.class);

    public void apply(Event event) {
        switch (event) {
            case TickStarted e -> {
                // The only thing a tick always does: move the clock.
                tick = e.tick();
            }
            case VillagerCreated e -> {
                tick = e.tick();
                villagers.put(e.id(), new Villager(e.id(), e.name(), e.traits(), Spot.HOME, e.neighbourhood()));
            }
            case VillagerMoved e -> {
                tick = e.tick();
                villager(e.id()).moveTo(e.spot(), e.tick());
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
            case TradeSeen e -> {
                tick = e.tick();
                // A sale starts a rumor family the same way reading the price does: the
                // first villager to conclude it opens one, everyone after joins it.
                if (!rumors.containsKey(e.rumorId())) {
                    addRumor(new Rumor(e.rumorId(), e.claim(), Rumor.MIN_SEVERITY,
                            Rumor.NO_PARENT, e.tick(), RumorOrigin.OBSERVED));
                }
                Rumor about = rumor(e.rumorId());
                Belief had = villager(e.villagerId()).belief(about.claim());
                NavigableSet<Integer> chain = new TreeSet<>();
                if (had != null) {
                    chain.addAll(had.chain());
                }
                // Not the market and not a villager. Nobody told them; they watched.
                chain.add(Belief.SEEN);
                villager(e.villagerId()).believe(new Belief(about.claim(), e.newConfidence(),
                        Belief.SEEN, e.tick(), e.rumorId(), chain));
            }
            case StockChecked e -> {
                tick = e.tick();
                Belief had = villager(e.villagerId()).belief(e.claim());
                // Only ever adjusts a belief already held, so there is always one here.
                // Who told them and what it passed through are left exactly as they were:
                // looking at a chest changes how sure you are, not where you heard it.
                villager(e.villagerId()).believe(new Belief(e.claim(), e.newConfidence(),
                        had.sourceId(), e.tick(), e.rumorId(), had.chain()));
            }
            case MarketNoiseSet e -> {
                tick = e.tick();
                marketNoiseLevel.put(Good.of(e.item()), e.level());
            }
            case MarketPriceSet e -> {
                tick = e.tick();
                marketPrice.put(Good.of(e.item()), e.price());
                priceHistory.computeIfAbsent(Good.of(e.item()), g -> new java.util.TreeMap<>())
                        .put(e.tick(), e.price());
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
                // From here on, this villager measures the price against this one.
                villager(e.villagerId()).sawPrice(Good.of(e.claim()), e.price());
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
     * been, plus the teller and everyone else who heard it said. The listener is dropped,
     * so nobody sits in their own chain.
     */
    private NavigableSet<Integer> chainAfter(RumorTold told, Claim claim) {
        NavigableSet<Integer> chain = new TreeSet<>();
        Belief tellerBelief = villager(told.tellerId()).belief(claim);
        if (tellerBelief != null) {
            chain.addAll(tellerBelief.chain());
        }
        chain.add(told.tellerId());
        // Everyone who heard it said. A villager who was standing there when this was
        // told is not an independent source for it afterwards, however sincerely they
        // repeat it later.
        chain.addAll(told.witnesses());
        chain.remove(told.listenerId());
        return chain;
    }

    private void addRumor(Rumor rumor) {
        rumors.put(rumor.id(), rumor);
        Good good = Good.of(rumor.claim());
        nextRumorId.put(good, Math.max(nextRumorId(good), rumor.id() + 1));
    }

    public long tick() { return tick; }

    /** Where a good's wobble stands, which a forked world has to carry with it. */
    public double marketNoiseLevel(Good good) { return marketNoiseLevel.getOrDefault(good, 0.0); }

    /** The last price a good's market settled on, empty until it first does. */
    public OptionalInt marketPrice(Good good) {
        Integer price = marketPrice.get(good);
        return price == null ? OptionalInt.empty() : OptionalInt.of(price);
    }

    /**
     * The market's recent level: the mean of the prices it settled on in the
     * {@code window} ticks before {@code tick}, not counting {@code tick} itself. When the
     * market was shut for all of them, the last price it settled on before; empty if it
     * has never settled at all.
     */
    public java.util.OptionalDouble trailingPrice(Good good, long tick, int window) {
        java.util.NavigableMap<Long, Integer> history = priceHistory.get(good);
        if (history == null) {
            return java.util.OptionalDouble.empty();
        }
        java.util.NavigableMap<Long, Integer> recent = history.subMap(tick - window, true, tick, false);
        if (!recent.isEmpty()) {
            double total = 0;
            for (int price : recent.values()) { // tick order
                total += price;
            }
            return java.util.OptionalDouble.of(total / recent.size());
        }
        Map.Entry<Long, Integer> before = history.lowerEntry(tick);
        return before == null ? java.util.OptionalDouble.empty()
                : java.util.OptionalDouble.of(before.getValue());
    }

    /** The id the next rumour about this good will take. */
    public int nextRumorId(Good good) { return nextRumorId.getOrDefault(good, good.firstRumorId()); }

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

    /**
     * The villager most likely to pass a rumor on. Ties go to the lower id, so the answer
     * never depends on iteration order.
     *
     * <p>Lives here so that everything choosing a planter — the demo, the sweeps, and the
     * calibration test — picks the same one. If they chose differently, the test would
     * stop checking the setting the sweeps actually validated.
     */
    public Villager gossipiestVillager() {
        Villager gossipiest = null;
        for (Villager villager : villagers.values()) { // id order
            if (gossipiest == null || villager.traits().gossip() > gossipiest.traits().gossip()) {
                gossipiest = villager;
            }
        }
        if (gossipiest == null) {
            throw new IllegalStateException("There are no villagers yet");
        }
        return gossipiest;
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
            && marketPrice.equals(w.marketPrice)
            && priceHistory.equals(w.priceHistory)
            && marketNoiseLevel.equals(w.marketNoiseLevel)
            && nextRumorId.equals(w.nextRumorId)
            && villagers.equals(w.villagers)
            && rumors.equals(w.rumors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tick, marketPrice, priceHistory, marketNoiseLevel, nextRumorId,
                villagers, rumors);
    }

    @Override
    public String toString() {
        return "tick=" + tick + ", prices=" + (marketPrice.isEmpty() ? "unset" : marketPrice)
                + ", villagers=" + villagers.size() + ", rumors=" + rumors.size();
    }
}
