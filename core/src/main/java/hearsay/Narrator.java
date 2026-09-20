package hearsay;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Turns events into readable lines. Presentation only: it reads the log and returns
 * strings, and never prints or touches the simulation. The same log can later feed the
 * terminal, a dashboard and the Minecraft plugin without the core knowing about any of
 * them.
 *
 * <p>It learns names from the log itself rather than from the world state, so it stays a
 * pure function of the events.
 */
public final class Narrator {

    private final Map<Integer, String> names = new TreeMap<>();

    /** One line for this event, or empty if it is not worth narrating. */
    public Optional<String> narrate(Event event) {
        return switch (event) {
            case VillagerCreated e -> {
                names.put(e.id(), e.name());
                yield Optional.of(prefix(e.tick()) + e.name() + " joins the village.");
            }
            case VillagersMet e -> Optional.of(prefix(e.tick())
                    + name(e.a()) + " meets " + name(e.b()) + " at " + e.spot().description() + ".");
            // Moves are implied by the meetings they cause, and twenty a tick would
            // drown out everything else. Prices get their own line once they mean
            // something in week 5.
            case VillagerMoved e -> Optional.empty();
            case PriceChanged e -> Optional.empty();
        };
    }

    /** Narrates a whole log in order, skipping the events with nothing to say. */
    public List<String> narrate(List<Event> events) {
        List<String> lines = new ArrayList<>();
        for (Event event : events) {
            narrate(event).ifPresent(lines::add);
        }
        return lines;
    }

    private String name(int id) {
        return names.getOrDefault(id, "villager " + id);
    }

    private static String prefix(long tick) {
        return "Day " + DayPart.dayOf(tick) + ", " + DayPart.of(tick).description() + ": ";
    }
}
