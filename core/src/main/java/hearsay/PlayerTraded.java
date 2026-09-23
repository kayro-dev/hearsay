package hearsay;

import java.util.Collections;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Somebody from outside sold diamonds to a villager, in front of whoever was standing there.
 *
 * <p>An input rather than a derived event, for the same reason {@link PlantRumor} is: the
 * player did it and the simulation did not decide it. A session with trades in it still
 * reproduces from seed + params + inputs, which is what lets a counterfactual ask what the
 * village would have done had the player kept their diamonds in their pocket.
 *
 * <p>A sale is not a price the village quoted itself — it quoted that price, so it learns
 * nothing from it. What it learns is that <em>somebody had diamonds to sell</em>, in a
 * village that may have been told there are none. That is evidence of plenty, and it is the
 * one piece of truth a player can produce on demand.
 *
 * @param item      what was sold. Each good is its own market, and selling gold is evidence
 *                  about gold and nothing else
 * @param count     how many changed hands, which with the good's value is the whole of the
 *                  evidence: sixteen diamonds is a glut and sixteen loaves is breakfast
 * @param emeralds  what was paid for them, kept for the ledger rather than for belief
 * @param witnesses everyone standing near enough to see it, the trader included. The same
 *                  shape as a telling's witnesses in {@link RumorTold}, and for the same
 *                  reason: two villagers who watched one sale are not two sources
 */
public record PlayerTraded(long tick, int villagerId, String item, int count, int emeralds,
                           NavigableSet<Integer> witnesses) implements Input {

    public PlayerTraded {
        if (count < 1) {
            throw new IllegalArgumentException("A trade moves at least one, was " + count);
        }
        // Only goods villagers buy. A player buying from a villager - bread, today - is not
        // evidence that anything is plentiful, and under "reality refutes, never asserts"
        // it may not create or strengthen a belief. Refused here rather than ignored later,
        // so no session can carry one and no replay can wonder what it meant.
        if (!Good.of(item).villagerBuys()) {
            throw new IllegalArgumentException("Villagers sell " + Good.of(item).plural()
                    + " rather than buy it; buying it is evidence of nothing");
        }
        witnesses = Collections.unmodifiableNavigableSet(new TreeSet<>(witnesses));
    }
}
