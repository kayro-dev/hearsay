package hearsay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class Simulation {
    private final Random random;
    private final WorldState state = new WorldState();
    private final List<Event> log = new ArrayList<>();
    private long tick = 0;

    public Simulation(long seed) {
        this.random = new Random(seed);
    }

    public void step() {
        tick++;
        int delta = random.nextInt(-3, 4); // -3 to +3
        record(new PriceChanged(tick, delta));
    }

    public void run(int ticks) {
        for (int i = 0; i < ticks; i++) {
            step();
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
