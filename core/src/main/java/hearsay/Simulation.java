package hearsay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

/**
 * Decides what happens, then writes it down. {@link #record(Event)} is the single door
 * every change to the world comes through.
 *
 * <p>A run is fully described by three things: the seed, the {@link Params}, and the list
 * of {@link Input}s. Given those, the whole event log follows.
 */
public final class Simulation {

    /**
     * Fixed names in a fixed order, so villager 3 is the same person for every run of a
     * seed. Village size is however many names there are.
     */
    private static final List<String> NAMES = List.of(
            "Mira", "Bo", "Ada", "Finn", "Nell", "Otto", "Ivy", "Rook", "Sela", "Tam",
            "Vero", "Wynn", "Gil", "Hana", "Jory", "Kit", "Lark", "Nix", "Pim", "Quill");

    public static final int VILLAGER_COUNT = NAMES.size();

    private final Params params;

    // One generator per subsystem. See RandomStream for why they must not be shared.
    private final Random movement;
    private final Random gossip;
    private final Random mutation;
    private final Random price;

    /** Inputs waiting for their tick, in the order they were given. */
    private final NavigableMap<Long, List<Input>> inputs = new TreeMap<>();

    private final WorldState state;
    private final List<Event> log = new ArrayList<>();
    private long tick = 0;

    public Simulation(long seed) {
        this(seed, Params.defaults(), List.of());
    }

    public Simulation(long seed, Params params, List<Input> inputs) {
        this.params = params;
        this.state = new WorldState(params);
        this.movement = RandomStream.MOVEMENT.from(seed);
        this.gossip = RandomStream.GOSSIP.from(seed);
        this.mutation = RandomStream.MUTATION.from(seed);
        this.price = RandomStream.PRICE.from(seed);
        for (Input input : inputs) {
            this.inputs.computeIfAbsent(input.tick(), t -> new ArrayList<>()).add(input);
        }
    }

    public void step() {
        tick++;
        if (tick == 1) {
            createVillagers();
        }
        applyInputs();
        moveEveryone(DayPart.of(tick));
        holdMeetings();
        record(new PriceChanged(tick, price.nextInt(-3, 4))); // placeholder until week 5
        if (DayPart.of(tick) == DayPart.NIGHT) {
            record(new DayEnded(tick));
        }
    }

    public void run(int ticks) {
        for (int i = 0; i < ticks; i++) {
            step();
        }
    }

    /**
     * The village is built by events like everything else. Creating villagers in the
     * constructor instead would put part of the world outside the log, and replay would
     * silently stop describing the whole world.
     */
    private void createVillagers() {
        for (int id = 0; id < VILLAGER_COUNT; id++) {
            // The order of these three rolls is part of the seed contract: reordering
            // them gives every seed a different village.
            Traits traits = new Traits(movement.nextDouble(), movement.nextDouble(), movement.nextDouble());
            record(new VillagerCreated(tick, id, NAMES.get(id), traits));
        }
    }

    /** Turns any inputs scheduled for this tick into input events. */
    private void applyInputs() {
        for (Input input : inputs.getOrDefault(tick, List.of())) {
            switch (input) {
                case PlantRumor p -> record(new RumorPlanted(
                        tick, state.nextRumorId(), p.claim(), p.severity(), p.villagerId()));
            }
        }
    }

    /** Every villager picks a spot for this part of the day, in id order. */
    private void moveEveryone(DayPart part) {
        for (Villager villager : state.villagers().values()) {
            record(new VillagerMoved(tick, villager.id(), pickSpot(part)));
        }
    }

    /** A weighted roll over the spots, in declaration order so the draw is reproducible. */
    private Spot pickSpot(DayPart part) {
        int total = 0;
        for (Spot spot : Spot.values()) {
            total += part.weight(spot);
        }
        int roll = movement.nextInt(total);
        for (Spot spot : Spot.values()) {
            roll -= part.weight(spot);
            if (roll < 0) {
                return spot;
            }
        }
        throw new IllegalStateException("Weights did not sum to the total for " + part);
    }

    /**
     * At every public spot, whoever is there is shuffled and paired off. An odd villager
     * out meets nobody this tick. Villagers only meet those standing next to them, which
     * is the brake that will make news spread in waves rather than all at once.
     */
    private void holdMeetings() {
        for (Spot spot : Spot.values()) {
            // HOME is not one place: it stands for twenty separate houses, so two
            // villagers being home at the same time are not in the same room. Pairing
            // here would turn every night into a village-wide mixing round, because the
            // night weights put nearly everyone home at once, and that would undo the
            // locality brake the rest of this method exists to create.
            if (spot == Spot.HOME) {
                continue;
            }

            List<Integer> present = new ArrayList<>();
            for (Villager villager : state.villagers().values()) {
                if (villager.spot() == spot) {
                    present.add(villager.id());
                }
            }
            Collections.shuffle(present, movement);
            for (int i = 0; i + 1 < present.size(); i += 2) {
                int a = present.get(i);
                int b = present.get(i + 1);
                record(new VillagersMet(tick, a, b, spot));
                exchangeNews(a, b);
            }
        }
    }

    /** Both villagers get a turn to speak, the lower id first so the order never varies. */
    private void exchangeNews(int a, int b) {
        int first = Math.min(a, b);
        int second = Math.max(a, b);
        maybeTell(first, second);
        maybeTell(second, first);
    }

    private void maybeTell(int tellerId, int listenerId) {
        Villager teller = state.villager(tellerId);
        Belief toTell = teller.strongestBeliefWorthTelling(params.tellThreshold());
        if (toTell == null) {
            return;
        }
        // Eager gossips with strong beliefs talk most.
        if (gossip.nextDouble() >= teller.traits().gossip() * toTell.confidence()) {
            return;
        }

        int rumorId = growInTheTelling(toTell.rumorId());
        Claim claim = state.rumor(rumorId).claim();
        double heard = confidenceAfterHearing(state.villager(listenerId), claim, toTell.confidence());
        record(new RumorTold(tick, tellerId, listenerId, rumorId, heard));
    }

    /**
     * A rumor sometimes gains a notch of severity as it passes on. The child keeps a link
     * to its parent, so the family tree stays intact.
     *
     * @return the rumor id the listener actually receives
     */
    private int growInTheTelling(int rumorId) {
        if (mutation.nextDouble() >= params.mutationChance()) {
            return rumorId;
        }
        Rumor told = state.rumor(rumorId);
        int grown = Math.min(Rumor.MAX_SEVERITY, told.severity() + 1);
        int childId = state.nextRumorId();
        record(new RumorMutated(tick, childId, rumorId, grown));
        return childId;
    }

    /**
     * How sure the listener ends up. Hearing something new lands at credulity times the
     * teller's own confidence; hearing it again closes part of the remaining gap, so
     * repetition strengthens a belief with diminishing returns and never passes 1.
     * Already believing the opposite makes the news less convincing.
     */
    private double confidenceAfterHearing(Villager listener, Claim claim, double tellerConfidence) {
        Belief held = listener.belief(claim);
        double credulity = listener.traits().credulity();
        double before = held == null ? 0 : held.confidence();

        double gain = held == null
                ? credulity * tellerConfidence
                : (1 - before) * credulity * tellerConfidence * params.repeatFactor();
        if (listener.belief(claim.opposite()) != null) {
            gain *= params.contradictionFactor();
        }
        return Math.min(1.0, before + gain);
    }

    private void record(Event event) {
        log.add(event);
        state.apply(event);
    }

    public Params params() { return params; }
    public WorldState state() { return state; }
    public List<Event> log() { return Collections.unmodifiableList(log); }

    /** Just the events that came from outside: the recipe a counterfactual varies. */
    public List<Event> inputEvents() {
        List<Event> found = new ArrayList<>();
        for (Event event : log) {
            if (event.isInput()) {
                found.add(event);
            }
        }
        return found;
    }

    /**
     * Rebuilds the world from an event list. Needs the params the log was produced with,
     * because {@link DayEnded}'s consequences are calculated rather than stored.
     */
    public static WorldState replay(List<Event> events, Params params) {
        WorldState fresh = new WorldState(params);
        for (Event event : events) {
            fresh.apply(event);
        }
        return fresh;
    }

    /** The inputs this simulation was given, by tick. */
    public Map<Long, List<Input>> inputs() {
        return Collections.unmodifiableMap(inputs);
    }
}
