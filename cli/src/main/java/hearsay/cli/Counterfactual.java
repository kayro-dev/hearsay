package hearsay.cli;

import hearsay.Bubble;
import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.Comparison;
import hearsay.Input;
import hearsay.Params;
import hearsay.PlantRumor;
import hearsay.PlantRumor;
import hearsay.RecipeFile;
import hearsay.Run;
import hearsay.Simulation;

import java.nio.file.Path;
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
        Claim claim = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);

        Run withLie;
        Input lie;
        String preamble;

        if (options.containsKey("file")) {
            // A session played in Minecraft. Its meetings were observed rather than
            // decided, and they are in the recipe, so both timelines get the same ones.
            Path file = Path.of(options.get("file"));
            withLie = RecipeFile.read(file);
            lie = firstLieIn(withLie);
            preamble = "Counterfactual: " + file.getFileName() + ", " + withLie.ticks()
                    + " ticks played in Minecraft.";
        } else {
            long seed = Options.longOption(options, "seed", 42);
            int ticks = Options.intOption(options, "ticks", 160);
            long toldAt = Options.longOption(options, "told-at", 41);
            int planter = Run.execute(seed, Params.defaults(), List.of(), 1)
                    .finalState().gossipiestVillager().id();
            lie = new PlantRumor(toldAt, claim, 1, planter);
            withLie = Run.execute(seed, Params.defaults(), List.of(lie), ticks);
            preamble = "Counterfactual: seed " + seed + ", " + ticks + " ticks.";
        }

        boolean played = options.containsKey("file");
        Run withoutLie = withLie.without(lie);
        Comparison comparison = Comparison.of(withLie.log(), withoutLie.log(), claim);

        int told = ((PlantRumor) lie).villagerId();
        System.out.println(preamble);
        System.out.printf("%s is told on tick %d that diamonds are scarce.%n",
                withLie.finalState().villager(told).name(), lie.tick());
        System.out.println("Both timelines share the same seed, params and inputs, so "
                + "everyone is in the same");
        System.out.println("place at the same time in both. The only difference is the lie.");
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
                + " (the lie was told on tick " + lie.tick() + ")");
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
        System.out.println("  each market day.");
        System.out.println();
        if (played) {
            System.out.println("  That is what happened in the village you played, and it is "
                    + "the only answer");
            System.out.println("  a played session can give. Minecraft decided where everybody "
                    + "walked, so there");
            System.out.println("  is no second future to roll: the many-worlds question needs "
                    + "a headless run.");
        } else {
            System.out.println("  That is one village and one roll of the dice: for how likely "
                    + "the lie was to");
            System.out.println("  cause it, run the worlds command.");
        }
    }

    /** The rumor a counterfactual removes. A session with none has nothing to ask about. */
    private static Input firstLieIn(Run run) {
        for (Input input : run.inputs()) {
            if (input instanceof PlantRumor plant) {
                return plant;
            }
        }
        throw new IllegalArgumentException("No rumor was ever planted in that session, so "
                + "there is no lie to take away. Use /hearsay rumor while playing.");
    }

    private static String price(int price) {
        return price == 0 ? "-" : String.valueOf(price);
    }
}
