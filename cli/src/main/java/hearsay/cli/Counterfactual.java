package hearsay.cli;

import hearsay.Bubble;
import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.Comparison;
import hearsay.Input;
import hearsay.Params;
import hearsay.PlantRumor;
import hearsay.Run;
import hearsay.Simulation;

import java.util.List;
import java.util.Map;

/**
 * One village, twice: with the lie and without it.
 *
 * <p>Every line of output says what kind of claim it is making. In a headless run every
 * actor is simulated, so this answer is exact within the model, and the wording says so
 * rather than leaving the reader to assume it applies to anything else.
 */
final class Counterfactual {

    private Counterfactual() {
    }

    static void print(String[] args) {
        Map<String, String> options = Options.parse(args);
        long seed = Options.longOption(options, "seed", 42);
        int ticks = Options.intOption(options, "ticks", 160);
        long toldAt = Options.longOption(options, "told-at", 41);
        Params params = Params.defaults();
        Claim claim = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

        int planter = Run.execute(seed, params, List.of(), 1)
                .finalState().gossipiestVillager().id();
        Input lie = new PlantRumor(toldAt, claim, 1, planter);

        Run withLie = Run.execute(seed, params, List.of(lie), ticks);
        Run withoutLie = withLie.without(lie);
        Comparison comparison = Comparison.of(withLie.log(), withoutLie.log(), claim);

        String planterName = withLie.finalState().villager(planter).name();
        System.out.printf("Counterfactual: seed %d, %d ticks. %s is told on tick %d that "
                + "diamonds are scarce.%n", seed, ticks, planterName, toldAt);
        System.out.println("Both timelines share the same seed and the same params, so "
                + "everyone walks the same");
        System.out.println("routes in both. The only difference is the lie.");
        System.out.println();
        System.out.println("                 with the lie          without it");
        System.out.println("  day    price  heard  believe |  price  heard  believe   price diff");

        for (Comparison.DayLine line : comparison.daily()) {
            System.out.printf("  %3d  %7s %6d %8d |%7s %6d %8d %12s%n",
                    line.day(), price(line.priceWith()), line.heardWith(), line.believersWith(),
                    price(line.priceWithout()), line.heardWithout(), line.believersWithout(),
                    line.priceWith() > 0 && line.priceWithout() > 0
                            ? String.format("%+d", line.priceDifference()) : "-");
        }

        System.out.println();
        System.out.println("  timelines diverge at tick "
                + comparison.divergenceTick().orElse(-1)
                + " (the lie was told on tick " + toldAt + ")");
        System.out.printf("  peak price:        %d with the lie, %d without%n",
                comparison.peakPriceWith(), comparison.peakPriceWithout());
        System.out.printf("  days above %d:     %d with the lie, %d without%n",
                Bubble.ELEVATED, comparison.daysAboveWith(Bubble.ELEVATED),
                comparison.daysAboveWithout(Bubble.ELEVATED));
        System.out.printf("  peak believers:    %d with the lie, %d without%n",
                comparison.peakBelieversWith(), comparison.peakBelieversWithout());
        System.out.println();
        System.out.printf("  In this simulated village, the lie added %d to the cost of buying "
                + "one diamond%n", comparison.extraCostOfADiamondEachMarketDay());
        System.out.println("  each market day. That is one village and one roll of the dice: "
                + "for how likely");
        System.out.println("  the lie was to cause it, run the worlds command.");
    }

    private static String price(int price) {
        return price == 0 ? "-" : String.valueOf(price);
    }
}
