package hearsay;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns events into readable lines. Presentation only: it reads the log and returns
 * strings, and never prints or touches the simulation. The same log can later feed the
 * terminal, a dashboard and the Minecraft plugin without the core knowing about any of
 * them.
 *
 * <p>It keeps its own {@link WorldState} and applies each event to it as it goes, rather
 * than tracking names and confidences by hand. That way the "before" figure in a line
 * like "0% → 42%" is read from the same apply() the simulation uses, and cannot drift
 * away from it.
 */
public final class Narrator {

    private final WorldState mirror;
    private final boolean narrateMeetings;

    /**
     * A mutation is recorded just before the telling it happens during, but reads better
     * afterwards, so its line waits for the telling. Presentation may reorder; the log
     * may not.
     */
    private String pendingMutation;

    private Narrator(Params params, boolean narrateMeetings) {
        this.mirror = new WorldState(params);
        this.narrateMeetings = narrateMeetings;
    }

    /** Narrates rumors: who told whom what, and where a rumor grew. */
    public static Narrator of(Params params) {
        return new Narrator(params, false);
    }

    /** Also narrates every meeting, which is a lot of lines once the village is busy. */
    public static Narrator withMeetings(Params params) {
        return new Narrator(params, true);
    }

    /** The lines this event produces: usually none or one, two when a rumor grew. */
    public List<String> narrate(Event event) {
        List<String> lines = new ArrayList<>();
        switch (event) {
            case VillagerCreated e -> {
                mirror.apply(e);
                lines.add(prefix(e.tick()) + e.name() + " joins the village.");
            }
            case RumorPlanted e -> {
                mirror.apply(e);
                lines.add(prefix(e.tick()) + name(e.villagerId()) + " gets the idea that "
                        + phrase(mirror.rumor(e.rumorId())) + ".");
            }
            case RumorMutated e -> {
                mirror.apply(e);
                pendingMutation = "…and it grew in the telling: "
                        + phraseNow(mirror.rumor(e.rumorId())) + ".";
            }
            case RumorTold e -> {
                Claim claim = mirror.rumor(e.rumorId()).claim();
                double before = confidenceIn(e.listenerId(), claim);
                String what = phrase(mirror.rumor(e.rumorId()));
                mirror.apply(e);

                lines.add(prefix(e.tick()) + name(e.tellerId()) + " tells " + name(e.listenerId())
                        + " that " + what + " (" + name(e.listenerId()) + ": "
                        + percent(before) + " → " + percent(e.newConfidence()) + ").");
                if (pendingMutation != null) {
                    lines.add(prefix(e.tick()) + pendingMutation);
                    pendingMutation = null;
                }
            }
            case VillagersMet e -> {
                mirror.apply(e);
                if (narrateMeetings) {
                    lines.add(prefix(e.tick()) + name(e.a()) + " meets " + name(e.b())
                            + " at " + e.spot().description() + ".");
                }
            }
            // Moves would be twenty lines a tick, the price walk means nothing until
            // week 5, and the end of a day is bookkeeping rather than news.
            case VillagerMoved e -> mirror.apply(e);
            case PriceChanged e -> mirror.apply(e);
            case DayEnded e -> mirror.apply(e);
        }
        return lines;
    }

    /** Narrates a whole log in order, skipping the events with nothing to say. */
    public List<String> narrate(List<Event> events) {
        List<String> lines = new ArrayList<>();
        for (Event event : events) {
            lines.addAll(narrate(event));
        }
        return lines;
    }

    private double confidenceIn(int villagerId, Claim claim) {
        Belief held = mirror.villager(villagerId).belief(claim);
        return held == null ? 0 : held.confidence();
    }

    private String name(int id) {
        return mirror.villagers().containsKey(id) ? mirror.villager(id).name() : "villager " + id;
    }

    private static String percent(double confidence) {
        return Math.round(confidence * 100) + "%";
    }

    /** e.g. "diamonds are very scarce" */
    private static String phrase(Rumor rumor) {
        return rumor.claim().item() + "s are " + strength(rumor);
    }

    /** e.g. "diamonds are now very scarce" */
    private static String phraseNow(Rumor rumor) {
        return rumor.claim().item() + "s are now " + strength(rumor);
    }

    private static String strength(Rumor rumor) {
        return switch (rumor.claim().type()) {
            case SCARCE -> switch (rumor.severity()) {
                case 1 -> "scarce";
                case 2 -> "very scarce";
                default -> "gone";
            };
            case ABUNDANT -> switch (rumor.severity()) {
                case 1 -> "plentiful";
                case 2 -> "everywhere";
                default -> "worthless";
            };
        };
    }

    private static String prefix(long tick) {
        return "Day " + DayPart.dayOf(tick) + ", " + DayPart.of(tick).description() + ": ";
    }
}
