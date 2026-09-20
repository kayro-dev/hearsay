package hearsay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Decides what happens, then writes it down. {@link #record(Event)} is the single door
 * every change to the world comes through.
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

    private final Random random;
    private final WorldState state = new WorldState();
    private final List<Event> log = new ArrayList<>();
    private long tick = 0;

    public Simulation(long seed) {
        this.random = new Random(seed);
    }

    public void step() {
        tick++;
        if (tick == 1) {
            createVillagers();
        }
        DayPart part = DayPart.of(tick);
        moveEveryone(part);
        holdMeetings();
        record(new PriceChanged(tick, random.nextInt(-3, 4))); // placeholder until week 5
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
            Traits traits = new Traits(random.nextDouble(), random.nextDouble(), random.nextDouble());
            record(new VillagerCreated(tick, id, NAMES.get(id), traits));
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
        int roll = random.nextInt(total);
        for (Spot spot : Spot.values()) {
            roll -= part.weight(spot);
            if (roll < 0) {
                return spot;
            }
        }
        throw new IllegalStateException("Weights did not sum to the total for " + part);
    }

    /**
     * At every spot, whoever is there is shuffled and paired off. An odd villager out
     * meets nobody this tick. Villagers only meet those standing next to them, which is
     * the brake that will make news spread in waves rather than all at once.
     */
    private void holdMeetings() {
        for (Spot spot : Spot.values()) {
            List<Integer> present = new ArrayList<>();
            for (Villager villager : state.villagers().values()) {
                if (villager.spot() == spot) {
                    present.add(villager.id());
                }
            }
            Collections.shuffle(present, random);
            for (int i = 0; i + 1 < present.size(); i += 2) {
                record(new VillagersMet(tick, present.get(i), present.get(i + 1), spot));
            }
        }
    }

    private void record(Event event) {
        log.add(event);
        state.apply(event);
    }

    public WorldState state() { return state; }
    public List<Event> log() { return Collections.unmodifiableList(log); }

    public static WorldState replay(List<Event> events) {
        WorldState fresh = new WorldState();
        for (Event event : events) {
            fresh.apply(event);
        }
        return fresh;
    }
}
