package hearsay;

import java.util.Random;

public final class Simulation {
    private final Random random;
    private long tick = 0;
    private long state = 0;

    public Simulation(long seed) {
        this.random = new Random(seed);
    }

    public void step() {
        tick++;
        state = state * 31 + random.nextInt(1000);
    }

    public void run(int ticks) {
        for (int i = 0; i < ticks; i++) {
            step();
        }
    }

    public long tick() { return tick; }
    public long state() { return state; }
}