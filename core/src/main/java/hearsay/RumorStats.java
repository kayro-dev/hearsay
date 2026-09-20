package hearsay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
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
 * <p>It replays the log through {@link WorldState#apply(Event)} rather than counting
 * tellings, so believers who have since forgotten are not still counted. That needs the
 * params the run used, which are part of the run's recipe anyway.
 */
public final class RumorStats {

    private final NavigableMap<Integer, List<Integer>> believersPerDay;
    private final NavigableMap<Integer, Long> ticksUntilHalf;
    private final NavigableMap<Integer, Double> reproductionNumber;
    private final NavigableMap<Integer, Claim> claims;

    private RumorStats(NavigableMap<Integer, List<Integer>> believersPerDay,
                       NavigableMap<Integer, Long> ticksUntilHalf,
                       NavigableMap<Integer, Double> reproductionNumber,
                       NavigableMap<Integer, Claim> claims) {
        this.believersPerDay = believersPerDay;
        this.ticksUntilHalf = ticksUntilHalf;
        this.reproductionNumber = reproductionNumber;
        this.claims = claims;
    }

    public static RumorStats of(List<Event> log, Params params) {
        WorldState mirror = new WorldState(params);

        Map<Integer, Integer> familyOf = new TreeMap<>();          // rumor id -> planted ancestor
        NavigableMap<Integer, List<Integer>> perDay = new TreeMap<>();
        NavigableMap<Integer, Long> half = new TreeMap<>();
        NavigableMap<Integer, Claim> claims = new TreeMap<>();
        Map<Integer, NavigableSet<Integer>> everBelieved = new TreeMap<>();
        Map<Integer, Map<Integer, Integer>> conversionsBy = new TreeMap<>();

        for (Event event : log) {
            // Register lineage before applying, so a telling can always be attributed.
            switch (event) {
                case RumorPlanted e -> {
                    familyOf.put(e.rumorId(), e.rumorId());
                    claims.put(e.rumorId(), e.claim());
                    perDay.put(e.rumorId(), new ArrayList<>());
                    everBelieved.put(e.rumorId(), new TreeSet<>());
                    conversionsBy.put(e.rumorId(), new TreeMap<>());
                }
                case RumorMutated e -> familyOf.put(e.rumorId(), familyOf.get(e.parentId()));
                default -> { }
            }

            if (event instanceof RumorTold told) {
                int family = familyOf.get(told.rumorId());
                boolean converted = !believes(mirror, told.listenerId(), family, familyOf);
                mirror.apply(event);
                everBelieved.get(family).add(told.listenerId());
                if (converted) {
                    conversionsBy.get(family).merge(told.tellerId(), 1, Integer::sum);
                    if (!half.containsKey(family)
                            && believers(mirror, family, familyOf) * 2 >= Simulation.VILLAGER_COUNT) {
                        half.put(family, told.tick());
                    }
                }
                continue;
            }

            mirror.apply(event);

            if (event instanceof RumorPlanted planted) {
                everBelieved.get(planted.rumorId()).add(planted.villagerId());
            }
            if (event instanceof DayEnded) {
                for (Integer family : perDay.keySet()) {
                    perDay.get(family).add(believers(mirror, family, familyOf));
                }
            }
        }

        NavigableMap<Integer, Double> reproduction = new TreeMap<>();
        for (Map.Entry<Integer, NavigableSet<Integer>> entry : everBelieved.entrySet()) {
            NavigableSet<Integer> believers = entry.getValue();
            Map<Integer, Integer> conversions = conversionsBy.get(entry.getKey());
            int converted = 0;
            for (Integer believer : believers) {
                converted += conversions.getOrDefault(believer, 0);
            }
            reproduction.put(entry.getKey(),
                    believers.isEmpty() ? 0.0 : converted / (double) believers.size());
        }

        return new RumorStats(perDay, half, reproduction, claims);
    }

    private static boolean believes(WorldState state, int villagerId, int family,
                                    Map<Integer, Integer> familyOf) {
        if (!state.villagers().containsKey(villagerId)) {
            return false;
        }
        for (Belief belief : state.villager(villagerId).beliefs().values()) {
            if (familyOf.getOrDefault(belief.rumorId(), -1) == family) {
                return true;
            }
        }
        return false;
    }

    private static int believers(WorldState state, int family, Map<Integer, Integer> familyOf) {
        int count = 0;
        for (Villager villager : state.villagers().values()) {
            if (believes(state, villager.id(), family, familyOf)) {
                count++;
            }
        }
        return count;
    }

    /** The planted rumor id of each family, in id order. */
    public NavigableSet<Integer> families() {
        return new TreeSet<>(believersPerDay.keySet());
    }

    public Claim claimOf(int family) {
        return claims.get(family);
    }

    /** How many villagers held this family's claim at the end of each day, day 1 first. */
    public List<Integer> believersPerDay(int family) {
        return Collections.unmodifiableList(believersPerDay.getOrDefault(family, List.of()));
    }

    /** The tick at which half the village first believed, if it ever did. */
    public OptionalLong ticksUntilHalfTheVillage(int family) {
        Long tick = ticksUntilHalf.get(family);
        return tick == null ? OptionalLong.empty() : OptionalLong.of(tick);
    }

    /** New believers converted per believer: above 1 means the rumor is still growing. */
    public double reproductionNumber(int family) {
        return reproductionNumber.getOrDefault(family, 0.0);
    }

    /** The largest number of simultaneous believers this family ever reached. */
    public int peakBelievers(int family) {
        int peak = 0;
        for (int count : believersPerDay(family)) {
            peak = Math.max(peak, count);
        }
        return peak;
    }
}
