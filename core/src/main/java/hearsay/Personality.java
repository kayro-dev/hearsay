package hearsay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * A readable name for what a villager is like, so the player can tell them apart.
 *
 * <p>Derived from {@link Traits}, which are rolled once at creation and never change, so a
 * villager's name is theirs for the whole run. Deliberately nothing to do with what they
 * believe: beliefs change every day, and a name that changed with them would be useless for
 * the thing a player needs it for, which is deciding whom to tell.
 *
 * <p><strong>Read-only, and tested to be.</strong> It reads a world and returns strings. No
 * decision anywhere consults a label, so adding it cost no experiment a re-run.
 *
 * <p>Labels are <em>relative and rationed</em>. The first attempt gave a label to anybody
 * past a fixed threshold, which on measurement labelled 78% of every village — a label on
 * sixteen villagers in twenty is a label on nobody. Instead each label goes to the single
 * most extreme villager for its trait, and a village gets only about one label per five
 * people, so the names stay worth reading.
 */
public final class Personality {

    /** How many villagers must share a village before it can support another name. */
    private static final int VILLAGERS_PER_LABEL = 5;

    /** How far past the middle a trait must sit to be worth naming at all. */
    private static final double HIGH = 0.70;
    private static final double LOW = 0.30;

    /**
     * The names, in the order they are offered. Gossip comes first because who *spreads* a
     * rumour matters more than who believes it: E8 put the planter at about a third of the
     * variance and E27 showed what aiming badly costs. The Town Crier is the one the player
     * most needs to find, so they are never crowded out by a Haggler.
     */
    private enum Name {
        TOWN_CRIER("the Town Crier", true),
        QUIET_ONE("the Quiet One", false),
        WORRIER("the Worrier", true),
        SCEPTIC("the Sceptic", false),
        HOARDER("the Hoarder", true),
        HAGGLER("the Haggler", false);

        private final String label;
        private final boolean high;

        Name(String label, boolean high) {
            this.label = label;
            this.high = high;
        }

        private double traitOf(Traits traits) {
            return switch (this) {
                case TOWN_CRIER, QUIET_ONE -> traits.gossip();
                case WORRIER, SCEPTIC -> traits.credulity();
                case HOARDER, HAGGLER -> traits.greed();
            };
        }

        private boolean qualifies(Traits traits) {
            double value = traitOf(traits);
            return high ? value >= HIGH : value <= LOW;
        }

        /** How far from the middle, which is how the remaining names are ranked. */
        private double extremeness(Traits traits) {
            return Math.abs(traitOf(traits) - 0.5);
        }
    }

    private Personality() {
    }

    /** How many names this village can carry. Always at least one, so somebody stands out. */
    public static int labelsFor(int villagers) {
        return Math.max(1, villagers / VILLAGERS_PER_LABEL);
    }

    /**
     * Who in this village answers to what, by villager id. Villagers with no name are
     * absent from the map, which is most of them.
     */
    public static Map<Integer, String> of(WorldState world) {
        int slots = labelsFor(world.villagers().size());
        Map<Integer, String> named = new LinkedHashMap<>();
        TreeSet<Integer> taken = new TreeSet<>();

        // The Town Crier first and out of turn, whatever the other traits are doing.
        award(world, Name.TOWN_CRIER, named, taken);

        // Then the most extreme remaining traits, measured by distance from the middle, so
        // a village whose loudest feature is a miser gets told about the miser.
        record Candidate(Name name, int villagerId, double extremeness) {}
        List<Candidate> rest = new ArrayList<>();
        for (Name name : Name.values()) {
            if (name == Name.TOWN_CRIER) {
                continue;
            }
            Villager most = mostExtreme(world, name);
            if (most != null) {
                rest.add(new Candidate(name, most.id(), name.extremeness(most.traits())));
            }
        }
        // Ties broken by villager id and then by the order the names are declared, so the
        // same village always produces the same names.
        rest.sort(Comparator.comparingDouble(Candidate::extremeness).reversed()
                .thenComparingInt(Candidate::villagerId)
                .thenComparing(c -> c.name().ordinal()));

        for (Candidate candidate : rest) {
            if (named.size() >= slots) {
                break;
            }
            if (taken.add(candidate.villagerId())) {
                named.put(candidate.villagerId(), candidate.name().label);
            }
        }
        return named;
    }

    private static void award(WorldState world, Name name,
                              Map<Integer, String> named, TreeSet<Integer> taken) {
        Villager most = mostExtreme(world, name);
        if (most != null && taken.add(most.id())) {
            named.put(most.id(), name.label);
        }
    }

    /**
     * The one villager this name belongs to, or null if nobody in the village is far enough
     * from the middle to deserve it.
     */
    private static Villager mostExtreme(WorldState world, Name name) {
        Villager best = null;
        for (Villager villager : world.villagers().values()) { // id order, so ties are stable
            if (!name.qualifies(villager.traits())) {
                continue;
            }
            if (best == null
                    || name.extremeness(villager.traits()) > name.extremeness(best.traits())) {
                best = villager;
            }
        }
        return best;
    }
}
