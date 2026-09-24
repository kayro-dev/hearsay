package hearsay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What one villager believes, how sure they are, and where it came from — in words.
 *
 * <p>Everything here is already in the world; none of it is worked out afresh. A belief
 * records the last person who told it, the chain it passed through on the way, and whether
 * it came from a villager, the market or the sight of something. This only reads that back.
 *
 * <p><strong>Read-only, and tested to be.</strong> It takes a {@link WorldState} and returns
 * strings. Nothing it does can change what any villager believes or what any sweep would
 * measure, which is what lets it be added without re-running a single experiment.
 */
public final class BeliefReport {

    private BeliefReport() {
    }

    /**
     * Everything this villager would tell you if you asked, one line per belief, in claim
     * order. Empty when they have heard nothing, which is most villagers most of the time.
     */
    public static List<String> of(WorldState world, int villagerId) {
        Villager villager = world.villager(villagerId);
        List<String> said = new ArrayList<>();
        for (Belief belief : villager.beliefs().values()) { // claim order, never hash order
            said.add(line(world, belief));
        }
        return said;
    }

    private static String line(WorldState world, Belief belief) {
        Rumor version = world.rumor(belief.rumorId());
        return String.format(Locale.ROOT, "%s (%s) — %s%s",
                describe(version),
                sureness(belief.confidence()),
                from(world, belief),
                throughWhom(belief));
    }

    /**
     * What they actually think, at the severity they heard it.
     *
     * <p>Items are stored singular — the claim is about "diamond" — and named here as the
     * good calls several of them. Appending an "s" gave "diamonds" by luck and would have
     * given "golds" by the same rule.
     */
    private static String describe(Rumor version) {
        return inWords(version.claim(), version.severity());
    }

    /** A claim at a severity, as a villager would say it: "wheat is running short". */
    static String inWords(Claim claim, int severity) {
        Good good = Good.of(claim);
        String item = good.plural() + " " + good.isOrAre();
        return switch (claim.type()) {
            case SCARCE -> switch (severity) {
                case 1 -> item + " getting scarce";
                case 2 -> item + " running short";
                default -> item + " all but gone";
            };
            case ABUNDANT -> switch (severity) {
                case 1 -> item + " easy to come by";
                case 2 -> item + " everywhere";
                default -> "there is a glut of " + item;
            };
        };
    }

    /**
     * How sure, in words rather than a percentage.
     *
     * <p>A villager asked what they think does not answer "73%". The number is on the
     * dashboard, where somebody is comparing runs rather than talking to a neighbour.
     */
    private static String sureness(double confidence) {
        if (confidence >= 0.85) {
            return "certain of it";
        }
        if (confidence >= 0.5) {
            return "fairly sure";
        }
        if (confidence >= 0.25) {
            return "half believes it";
        }
        return "barely credits it";
    }

    /** Who they had it from, which the belief has been carrying all along. */
    private static String from(WorldState world, Belief belief) {
        return switch (belief.sourceId()) {
            case Belief.NO_SOURCE -> "somebody told them so directly";
            case Belief.MARKET -> "saw it at the market";
            case Belief.SEEN -> "witnessed a sale";
            default -> "heard it from " + world.villager(belief.sourceId()).name();
        };
    }

    /**
     * How far it travelled to reach them, which is the part a player cannot see by watching.
     *
     * <p>The chain holds everyone the belief passed through, and its size is the honest
     * answer to "is this going round the village". One name is a conversation; six is a
     * rumour with a history.
     */
    private static String throughWhom(Belief belief) {
        int hands = belief.chain().size();
        if (hands <= 1) {
            return "";
        }
        return ", after passing through " + hands + " people";
    }
}
