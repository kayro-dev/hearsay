package hearsay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * How far a rumor got. Reads an event log and nothing else, the same way the dashboard
 * will later: analysis never reaches into the simulation's internals.
 *
 * <p>Rumors are grouped into families. A rumor that grew in the telling is counted with
 * the planted rumor it descends from, so "how far did that lie travel" has one answer
 * even after the lie has been exaggerated a dozen times.
 *
 * <p>Distinguishes <em>heard</em> from <em>believes</em>. A villager has heard a claim if
 * they hold it at any strength; they believe it if their confidence reaches
 * {@link #DEFAULT_BELIEVE_THRESHOLD}. Reporting only the first overstates a rumor's grip
 * badly: a claim can reach half the village while convincing nobody.
 *
 * <p>The threshold lives here rather than in {@link Params} because it changes nothing
 * about the run. Params are part of the recipe that reproduces a log; a reporting
 * threshold that sat there would make the recipe claim a difference it does not make.
 */
public final class RumorStats {

    /** Confidence at which a villager counts as believing rather than merely informed. */
    public static final double DEFAULT_BELIEVE_THRESHOLD = 0.5;

    /** What one family looked like at the end of one day. */
    public record DayStats(int day, int heard, int believes, NavigableMap<Integer, Integer> bySeverity) {
        public DayStats {
            bySeverity = Collections.unmodifiableNavigableMap(new TreeMap<>(bySeverity));
        }

        /** The most severe version anyone was holding that day, or 0 if nobody held it. */
        public int worstSeverityHeld() {
            return bySeverity.isEmpty() ? 0 : bySeverity.lastKey();
        }
    }

    private final double believeThreshold;
    private final NavigableMap<Integer, List<Double>> lifetimeDays;
    private final NavigableMap<Integer, List<DayStats>> daily;
    private final NavigableMap<Integer, Integer> everHeard;
    private final NavigableMap<Integer, Long> halfHeard;
    private final NavigableMap<Integer, Long> halfBelieves;
    private final NavigableMap<Integer, Double> reproduction;
    private final NavigableMap<Integer, Claim> claims;

    private RumorStats(double believeThreshold,
                       NavigableMap<Integer, List<Double>> lifetimeDays,
                       NavigableMap<Integer, List<DayStats>> daily,
                       NavigableMap<Integer, Integer> everHeard,
                       NavigableMap<Integer, Long> halfHeard,
                       NavigableMap<Integer, Long> halfBelieves,
                       NavigableMap<Integer, Double> reproduction,
                       NavigableMap<Integer, Claim> claims) {
        this.believeThreshold = believeThreshold;
        this.lifetimeDays = lifetimeDays;
        this.daily = daily;
        this.everHeard = everHeard;
        this.halfHeard = halfHeard;
        this.halfBelieves = halfBelieves;
        this.reproduction = reproduction;
        this.claims = claims;
    }

    public static RumorStats of(List<Event> log) {
        return of(log, DEFAULT_BELIEVE_THRESHOLD);
    }

    public static RumorStats of(List<Event> log, double believeThreshold) {
        WorldState mirror = new WorldState();

        Map<Integer, Integer> familyOf = new TreeMap<>();            // rumor id -> planted ancestor
        NavigableMap<Integer, List<DayStats>> daily = new TreeMap<>();
        NavigableMap<Integer, Claim> claims = new TreeMap<>();
        NavigableMap<Integer, Long> halfHeard = new TreeMap<>();
        NavigableMap<Integer, Long> halfBelieves = new TreeMap<>();
        Map<Integer, NavigableSet<Integer>> everHeardBy = new TreeMap<>();
        Map<Integer, Map<Integer, Integer>> conversionsBy = new TreeMap<>();
        NavigableMap<Integer, List<Double>> lifetimeDays = new TreeMap<>();
        // villager and family -> the tick their confidence last rose above the threshold
        Map<String, Long> aboveSince = new TreeMap<>();

        int day = 0;
        for (Event event : log) {
            // Register lineage before applying, so a telling can always be attributed.
            switch (event) {
                case RumorPlanted e -> {
                    familyOf.put(e.rumorId(), e.rumorId());
                    claims.put(e.rumorId(), e.claim());
                    daily.put(e.rumorId(), new ArrayList<>());
                    lifetimeDays.put(e.rumorId(), new ArrayList<>());
                    everHeardBy.put(e.rumorId(), new TreeSet<>());
                    conversionsBy.put(e.rumorId(), new TreeMap<>());
                }
                case RumorMutated e -> familyOf.put(e.rumorId(), familyOf.get(e.parentId()));
                default -> { }
            }

            boolean reached = event instanceof RumorTold;
            // Attributed to the version the listener ends up holding, since that is what a
            // later snapshot will find in their head.
            int family = reached ? familyOf.get(((RumorTold) event).keptRumorId()) : -1;
            boolean wasHolding = reached
                    && beliefIn(mirror, ((RumorTold) event).listenerId(), family, familyOf) != null;

            mirror.apply(event);

            if (event instanceof RumorPlanted planted) {
                everHeardBy.get(planted.rumorId()).add(planted.villagerId());
            }
            if (event instanceof RumorTold told) {
                everHeardBy.get(family).add(told.listenerId());
                if (!wasHolding) {
                    conversionsBy.get(family).merge(told.tellerId(), 1, Integer::sum);
                }
            }
            if (event instanceof RumorTold || event instanceof RumorPlanted) {
                for (int known : daily.keySet()) {
                    noteHalfway(mirror, known, familyOf, believeThreshold,
                            halfHeard, halfBelieves, event.tick());
                }
            }
            if (event instanceof RumorTold || event instanceof RumorPlanted
                    || event instanceof DayEnded) {
                for (int known : daily.keySet()) {
                    trackSpells(mirror, known, familyOf, believeThreshold,
                            aboveSince, lifetimeDays.get(known), event.tick());
                }
            }
            if (event instanceof DayEnded) {
                day++;
                for (int known : daily.keySet()) {
                    daily.get(known).add(snapshot(mirror, known, familyOf, believeThreshold, day));
                }
            }
        }

        NavigableMap<Integer, Double> reproduction = new TreeMap<>();
        NavigableMap<Integer, Integer> everHeard = new TreeMap<>();
        for (Map.Entry<Integer, NavigableSet<Integer>> entry : everHeardBy.entrySet()) {
            NavigableSet<Integer> reachedVillagers = entry.getValue();
            Map<Integer, Integer> conversions = conversionsBy.get(entry.getKey());
            int converted = 0;
            for (int villager : reachedVillagers) {
                converted += conversions.getOrDefault(villager, 0);
            }
            everHeard.put(entry.getKey(), reachedVillagers.size());
            reproduction.put(entry.getKey(),
                    reachedVillagers.isEmpty() ? 0.0 : converted / (double) reachedVillagers.size());
        }

        return new RumorStats(believeThreshold, lifetimeDays, daily, everHeard,
                halfHeard, halfBelieves, reproduction, claims);
    }

    /**
     * Notices villagers crossing the believing threshold and records how long they stayed
     * over it, in days. A spell still running when the log ends is left out rather than
     * counted short, so the figure describes beliefs that actually finished.
     */
    private static void trackSpells(WorldState state, int family, Map<Integer, Integer> familyOf,
                                    double threshold, Map<String, Long> aboveSince,
                                    List<Double> lifetimeDays, long tick) {
        for (Villager villager : state.villagers().values()) {
            String who = villager.id() + "/" + family;
            Belief belief = beliefIn(state, villager.id(), family, familyOf);
            boolean believing = belief != null && belief.confidence() >= threshold;

            if (believing && !aboveSince.containsKey(who)) {
                aboveSince.put(who, tick);
            } else if (!believing && aboveSince.containsKey(who)) {
                lifetimeDays.add((tick - aboveSince.remove(who)) / (double) TICKS_PER_DAY);
            }
        }
    }

    private static final int TICKS_PER_DAY = 4;

    private static void noteHalfway(WorldState state, int family, Map<Integer, Integer> familyOf,
                                    double threshold, NavigableMap<Integer, Long> halfHeard,
                                    NavigableMap<Integer, Long> halfBelieves, long tick) {
        DayStats now = snapshot(state, family, familyOf, threshold, 0);
        int villagers = state.villagers().size();
        if (villagers == 0) {
            return;
        }
        if (!halfHeard.containsKey(family) && now.heard() * 2 >= villagers) {
            halfHeard.put(family, tick);
        }
        if (!halfBelieves.containsKey(family) && now.believes() * 2 >= villagers) {
            halfBelieves.put(family, tick);
        }
    }

    private static DayStats snapshot(WorldState state, int family, Map<Integer, Integer> familyOf,
                                     double threshold, int day) {
        int heard = 0;
        int believes = 0;
        NavigableMap<Integer, Integer> bySeverity = new TreeMap<>();
        for (Villager villager : state.villagers().values()) {
            Belief belief = beliefIn(state, villager.id(), family, familyOf);
            if (belief == null) {
                continue;
            }
            heard++;
            if (belief.confidence() >= threshold) {
                believes++;
            }
            bySeverity.merge(state.rumor(belief.rumorId()).severity(), 1, Integer::sum);
        }
        return new DayStats(day, heard, believes, bySeverity);
    }

    /** What this villager holds from this rumor family, or null if nothing. */
    private static Belief beliefIn(WorldState state, int villagerId, int family,
                                   Map<Integer, Integer> familyOf) {
        if (!state.villagers().containsKey(villagerId)) {
            return null;
        }
        for (Belief belief : state.villager(villagerId).beliefs().values()) {
            if (familyOf.getOrDefault(belief.rumorId(), -1) == family) {
                return belief;
            }
        }
        return null;
    }

    public double believeThreshold() { return believeThreshold; }

    /** The planted rumor id of each family, in id order. */
    public NavigableSet<Integer> families() {
        return new TreeSet<>(daily.keySet());
    }

    public Claim claimOf(int family) { return claims.get(family); }

    /** One entry per day, day 1 first. */
    public List<DayStats> daily(int family) {
        return Collections.unmodifiableList(daily.getOrDefault(family, List.of()));
    }

    /** How many villagers ever held this claim, whether or not they still do. */
    public int everHeard(int family) {
        return everHeard.getOrDefault(family, 0);
    }

    public int peakHeard(int family) {
        int peak = 0;
        for (DayStats stats : daily(family)) {
            peak = Math.max(peak, stats.heard());
        }
        return peak;
    }

    public int peakBelieves(int family) {
        int peak = 0;
        for (DayStats stats : daily(family)) {
            peak = Math.max(peak, stats.believes());
        }
        return peak;
    }

    public OptionalLong ticksUntilHalfHeard(int family) {
        return at(halfHeard, family);
    }

    public OptionalLong ticksUntilHalfBelieves(int family) {
        return at(halfBelieves, family);
    }

    private static OptionalLong at(NavigableMap<Integer, Long> ticks, int family) {
        Long tick = ticks.get(family);
        return tick == null ? OptionalLong.empty() : OptionalLong.of(tick);
    }

    /**
     * How many days the claim held at least half as many believers as it ever had. A
     * plainer "days with any believer" counts a single stubborn holdout as though the
     * village still believed, which overstates how long a rumor had a grip.
     */
    public int daysAtLeastHalfPeak(int family) {
        int peak = peakBelieves(family);
        if (peak == 0) {
            return 0;
        }
        int days = 0;
        for (DayStats stats : daily(family)) {
            if (stats.believes() * 2 >= peak) {
                days++;
            }
        }
        return days;
    }

    /**
     * The middle length, in days, of a spell spent believing this claim: how long one
     * villager stays above the threshold before fading back under it. Empty when no spell
     * finished within the log.
     */
    public OptionalDouble medianBeliefLifetimeDays(int family) {
        List<Double> spells = new ArrayList<>(lifetimeDays.getOrDefault(family, List.of()));
        if (spells.isEmpty()) {
            return OptionalDouble.empty();
        }
        Collections.sort(spells);
        int middle = spells.size() / 2;
        return OptionalDouble.of(spells.size() % 2 == 1
                ? spells.get(middle)
                : (spells.get(middle - 1) + spells.get(middle)) / 2);
    }

    /** How many finished spells of believing went into the median. */
    public int completedBeliefSpells(int family) {
        return lifetimeDays.getOrDefault(family, List.of()).size();
    }

    /**
     * New people reached per person reached: transmission, not conviction. Above 1 means
     * the claim was still finding fresh ears.
     */
    public double reproductionNumber(int family) {
        return reproduction.getOrDefault(family, 0.0);
    }
}
