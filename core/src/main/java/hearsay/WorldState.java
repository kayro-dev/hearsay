package hearsay;

import java.util.Objects;

public final class WorldState {
    private long tick = 0;
    private int diamondPrice = 100;

    public void apply(Event event) {
        switch (event) {
            case PriceChanged e -> {
                tick = e.tick();
                diamondPrice = Math.max(1, diamondPrice + e.delta());
            }
        }
    }

    public long tick() { return tick; }
    public int diamondPrice() { return diamondPrice; }

    @Override
    public boolean equals(Object o) {
        return o instanceof WorldState w
            && tick == w.tick && diamondPrice == w.diamondPrice;
    }

    @Override
    public int hashCode() { return Objects.hash(tick, diamondPrice); }

    @Override
    public String toString() { return "tick=" + tick + ", diamondPrice=" + diamondPrice; }
}
