package hearsay.cli;

import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.Comparison;
import hearsay.PairedWorlds;
import hearsay.Params;
import hearsay.PlantRumor;
import hearsay.Run;
import hearsay.Simulation;

import java.util.List;
import java.util.Map;

/**
 * Many worlds, in pairs, to say how likely the lie was to cause what happened.
 *
 * <p>The wording is part of the result. A single run can only say what happened in one
 * village; this says in how many of N paired worlds the lie made the difference, and the
 * output states that rather than leaving it to be assumed.
 */
final class Worlds {

    private Worlds() {
    }

    static void print(String[] args) {
        Map<String, String> options = Options.parse(args);
        long seed = Options.longOption(options, "seed", 42);
        int pairs = Options.intOption(options, "pairs", 50);
        int ticks = Options.intOption(options, "ticks", 160);
        long toldAt = Options.longOption(options, "told-at", 41);
        Params params = Params.defaults();
        Claim claim = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

        int planter = Run.execute(seed, params, List.of(), 1)
                .finalState().gossipiestVillager().id();
        PlantRumor lie = new PlantRumor(toldAt, claim, 1, planter);

        PairedWorlds worlds = PairedWorlds.run(seed, params, lie, ticks, pairs);

        System.out.printf("Paired worlds: seed %d, %d pairs, %d ticks, the lie told on tick %d.%n",
                seed, pairs, ticks, toldAt);
        System.out.println("Each pair shares one future: the same branch seed with the lie "
                + "and without it,");
        System.out.println("so within a pair the only difference is the lie and what it caused.");
        System.out.println();
        System.out.println("  pair      seed   peak$ with  without   diff   burst?   extra cost");

        for (PairedWorlds.Pair pair : worlds.pairs()) {
            Comparison comparison = pair.comparedOn(claim);
            System.out.printf("  %4d  %8s   %9d %8d %6s %8s %12d%n",
                    pair.index(), Long.toHexString(pair.branchSeed()).substring(0, 6),
                    comparison.peakPriceWith(), comparison.peakPriceWithout(),
                    String.format("%+d", comparison.peakPriceWith() - comparison.peakPriceWithout()),
                    bubbled(pair, claim), comparison.extraCostOfADiamondEachMarketDay());
        }

        int caused = worlds.pairsWhereTheLieCausedABubble(claim);
        int withLie = worlds.worldsThatBubbledWithTheLie(claim);
        int withoutLie = worlds.worldsThatBubbledWithoutIt(claim);

        System.out.println();
        System.out.printf("  bubbled with the lie:     %d of %d worlds%n", withLie, pairs);
        System.out.printf("  bubbled without it:       %d of %d worlds%n", withoutLie, pairs);
        System.out.printf("  mean peak price effect:   %+.1f%n",
                worlds.meanPeakPriceDifference(claim));
        System.out.printf("  mean extra cost:          %+.1f per world%n", worlds.meanExtraCost(claim));
        System.out.println();
        System.out.printf("  In %d of %d paired worlds the lie made the difference: the price "
                + "bubbled and%n", caused, pairs);
        System.out.println("  burst where it was told, and did not where it was not. Every "
                + "figure here is");
        System.out.println("  within the model, with every actor simulated.");
    }

    /** Whether this pair's bubble can be laid at the lie's door. */
    private static String bubbled(PairedWorlds.Pair pair, Claim claim) {
        boolean with = pair.bubbledWithTheLie(claim);
        boolean without = pair.bubbledWithoutIt(claim);
        if (with && !without) {
            return "lie";
        }
        return with ? "both" : (without ? "anyway" : "no");
    }
}
