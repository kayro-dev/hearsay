package hearsay;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * What the screen shows of one claim: who holds it and how firmly, what everyone would
 * charge for its good, and the price the market settled on.
 *
 * <p>One claim at a time, chosen by the player. A village trading four goods has four
 * prices and eight claims, and a head carrying all of them is the clutter the labels were
 * cut down to get rid of. Until this existed every display followed diamonds scarce
 * whatever was planted, so a wheat rumour spread with nothing visible changing.
 *
 * <p><strong>Read-only, and tested to be.</strong> It takes the simulation and returns
 * numbers, so which claim is on display can never change what the village does or what
 * any sweep measures.
 *
 * @param confidences how firmly each villager holding the claim holds it, by id; villagers
 *                    who have not heard it are absent rather than zero
 * @param asks        what each villager would charge for the claim's good, as a price index
 * @param price       the good's market price, empty until the market has opened
 */
public record OnDisplay(Claim claim, Map<Integer, Double> confidences,
                        Map<Integer, Integer> asks, OptionalInt price) {

    public OnDisplay {
        confidences = Map.copyOf(confidences);
        asks = Map.copyOf(asks);
    }

    public static OnDisplay of(Simulation simulation, Claim claim) {
        Good good = Good.of(claim);
        Map<Integer, Double> confidences = new LinkedHashMap<>();
        Map<Integer, Integer> asks = new LinkedHashMap<>();
        simulation.state().villagers().forEach((id, villager) -> { // id order
            Belief held = villager.belief(claim);
            if (held != null) {
                confidences.put(id, held.confidence());
            }
            asks.put(id, (int) Math.round(simulation.askingPrice(villager, good)));
        });
        return new OnDisplay(claim, confidences, asks, simulation.state().marketPrice(good));
    }

    public Good good() {
        return Good.of(claim);
    }
}
