package hearsay;

import java.util.ArrayList;
import java.util.List;

/**
 * What the lie did, what the selling did, and how to tell them apart.
 *
 * <p>Two timelines answer one question. Once the player can trade, there are two things
 * that moved the price and a single counterfactual cannot say which. So four runs, from
 * the same seed and the same everything else:
 *
 * <ul>
 *   <li><strong>as played</strong> — the lie and the trades</li>
 *   <li><strong>no lie</strong> — the trades alone, on a village nobody lied to</li>
 *   <li><strong>no trades</strong> — the lie alone, on a village nobody traded with</li>
 *   <li><strong>neither</strong> — the village left entirely alone</li>
 * </ul>
 *
 * <p>The lie's contribution is what happened minus what the trades would have done without
 * it. The selling's contribution is what happened minus what the lie would have done
 * without it. Reporting only the first would credit the lie with the player's own market
 * footprint, which is the trap this class exists to avoid.
 */
public record Footprint(MarketStats asPlayed, MarketStats withoutTheLie,
                        MarketStats withoutTheTrades, MarketStats leftAlone) {

    public static Footprint of(Run played, Claim claim) {
        List<Input> noLie = new ArrayList<>();
        List<Input> noTrades = new ArrayList<>();
        List<Input> neither = new ArrayList<>();
        for (Input input : played.inputs()) {
            if (!(input instanceof PlantRumor)) {
                noLie.add(input);
            }
            if (!(input instanceof PlayerTraded)) {
                noTrades.add(input);
            }
            if (!(input instanceof PlantRumor) && !(input instanceof PlayerTraded)) {
                neither.add(input);
            }
        }
        return new Footprint(
                MarketStats.of(played.log(), claim),
                statsFor(played, noLie, claim),
                statsFor(played, noTrades, claim),
                statsFor(played, neither, claim));
    }

    private static MarketStats statsFor(Run played, List<Input> inputs, Claim claim) {
        return MarketStats.of(
                Run.execute(played.seed(), played.params(), inputs, played.ticks()).log(), claim);
    }

    /**
     * How much of the peak price the lie is responsible for, holding the player's trades
     * fixed: what happened, less what the same trades would have done to a village nobody
     * had lied to.
     */
    public int peakFromTheLie() {
        return asPlayed.peakPrice() - withoutTheLie.peakPrice();
    }

    /**
     * How much the player's own selling moved the peak, holding the lie fixed.
     *
     * <p>Reported beside {@link #peakFromTheLie()} and never folded into it. A player who
     * sells into a village is moving the price themselves, and a figure that quietly
     * credited the lie with that would be flattering the lie.
     */
    public int peakFromTheTrading() {
        return asPlayed.peakPrice() - withoutTheTrades.peakPrice();
    }

    /** What the village would have done with nobody interfering at all. */
    public int peakLeftAlone() {
        return leftAlone.peakPrice();
    }

    /**
     * True when the two are not simply additive — when the lie and the trades together did
     * something neither would have done apart.
     *
     * <p>Worth reporting rather than hiding. Selling into a panic is supposed to interact
     * with the panic; if the numbers ever add up exactly, the mechanism is not doing
     * anything interesting.
     */
    public int interaction() {
        return asPlayed.peakPrice() - withoutTheLie.peakPrice()
                - withoutTheTrades.peakPrice() + leftAlone.peakPrice();
    }
}
