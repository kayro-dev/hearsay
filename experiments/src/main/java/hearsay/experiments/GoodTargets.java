package hearsay.experiments;

import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.Estimate;
import hearsay.Event;
import hearsay.Good;
import hearsay.Input;
import hearsay.MarketPriceSet;
import hearsay.MarketStats;
import hearsay.Params;
import hearsay.PlantRumor;
import hearsay.Run;
import hearsay.Target;

import java.util.ArrayList;
import java.util.List;

/**
 * Judges a new good against diamond, on the same seeds, with targets that cannot be bare.
 *
 * <p>The stage 2 gate for every good after diamond. Each good is supposed to be diamond's
 * market with different dice, so almost every target is a comparison with diamond measured
 * on exactly the same seeds, judged on the interval of the difference. Absolute bands are
 * used only where the README states a figure as a claim.
 *
 * <p>{@code ./gradlew :experiments:goods --args="--good iron"}
 */
public final class GoodTargets {

    private static final Target.Band BURSTS = Target.demonstrates(
            "a lie bursts the price within 30 days", 0.25, 0.60, 100);
    private static final Target.SameAs BURSTS_LIKE_DIAMOND = Target.sameAs(
            "bursts within 30 days, as diamond does", 100);
    private static final Target.SameAs QUIET_LIKE_DIAMOND = Target.sameAs(
            "quiet bursts per 100 village-days, as diamond", 60);
    private static final Target.SameAs DECAY_LIKE_DIAMOND = Target.sameAs(
            "swing decay after the largest swing, as diamond", 30);
    private static final Target.SameAs SETTLES_LIKE_DIAMOND = Target.sameAs(
            "settled within 10% over 175 days, as diamond", 200);
    private static final Target.Band COMES_HOME = Target.demonstrates(
            "last-quarter price within 5% of normal", 95, 105, 200);
    private static final Target.SameAs SEPARATES_LIKE_DIAMOND = Target.sameAs(
            "paired worlds bubbling only with the lie, as diamond", 200);
    private static final Target.Band NEVER_WITHOUT = Target.demonstrates(
            "paired worlds bubbling only without the lie", 0.0, 0.03, 200);

    private GoodTargets() {
    }

    /** Everything measured about one good. */
    private record Measured(Estimate bursts, Estimate quiet, Estimate decay, Estimate settled,
                            Estimate endPrice, Estimate onlyWith, Estimate onlyWithout) {}

    public static void main(String[] args) {
        Good good = Good.of(Cli.parse(args).getOrDefault("good", "gold"));
        if (good == Good.DIAMOND) {
            throw new IllegalArgumentException("diamond is the reference; name another good");
        }
        Params village = Params.defaults().withGoods(Good.DIAMOND, good);

        System.out.println("Judging " + good.plural() + " against diamonds on the same seeds.");
        Measured diamond = measure(Good.DIAMOND, village);
        Measured other = measure(good, village);

        List<Target.Verdict> verdicts = List.of(
                BURSTS.judge(other.bursts()),
                BURSTS_LIKE_DIAMOND.judge(other.bursts(), diamond.bursts()),
                QUIET_LIKE_DIAMOND.judge(other.quiet(), diamond.quiet()),
                DECAY_LIKE_DIAMOND.judge(other.decay(), diamond.decay()),
                SETTLES_LIKE_DIAMOND.judge(other.settled(), diamond.settled()),
                COMES_HOME.judge(other.endPrice()),
                SEPARATES_LIKE_DIAMOND.judge(other.onlyWith(), diamond.onlyWith()),
                NEVER_WITHOUT.judge(other.onlyWithout()));

        System.out.println();
        System.out.println("                                diamond                    " + good.id());
        row("bursts within 30 days", diamond.bursts(), other.bursts());
        row("quiet bursts / 100 village-days", diamond.quiet(), other.quiet());
        row("decay after the largest swing", diamond.decay(), other.decay());
        row("settled within 10%", diamond.settled(), other.settled());
        row("last-quarter price", diamond.endPrice(), other.endPrice());
        row("paired: only with the lie", diamond.onlyWith(), other.onlyWith());
        row("paired: only without", diamond.onlyWithout(), other.onlyWithout());
        System.out.println();
        verdicts.forEach(System.out::println);
        boolean all = verdicts.stream().allMatch(Target.Verdict::passed);
        System.out.println();
        System.out.println(all ? good.plural() + " PASS every target, untuned."
                : good.plural() + " FAIL. Investigate independence before touching a parameter.");
    }

    private static void row(String what, Estimate d, Estimate o) {
        System.out.printf("  %-30s %-26s %s%n", what, d, o);
    }

    private static Measured measure(Good good, Params village) {
        Claim claim = new Claim(good.id(), ClaimType.SCARCE);

        int bursts = 0;
        for (long seed = 1001; seed < 1101; seed++) {
            if (lied(seed, village, claim, 200).bubbleWithin(1, 30).isPresent()) {
                bursts++;
            }
        }

        int[] quietCounts = new int[60];
        for (int i = 0; i < 60; i++) {
            quietCounts[i] = MarketStats.of(
                    Run.execute(3001 + i, village, List.of(), 2000).log(), claim).bubbles().size();
        }

        List<Double> decays = new ArrayList<>();
        int settled = 0;
        double[] endPrices = new double[200];
        for (int i = 0; i < 200; i++) {
            long seed = 1001 + i;
            Run run = Run.execute(seed, village, lie(seed, village, claim), 700);
            MarketStats stats = MarketStats.of(run.log(), claim);
            stats.swingDecayAfterPeak(10).ifPresent(decays::add);
            if (stats.settledAt(100, 0.10).isPresent()) {
                settled++;
            }
            endPrices[i] = lastQuarterPrice(run.log(), good);
        }

        int onlyWith = 0;
        int onlyWithout = 0;
        for (long seed = 2001; seed < 2201; seed++) {
            boolean with = lied(seed, village, claim, 200).bubbleWithin(1, 30).isPresent();
            boolean without = MarketStats.of(Run.execute(seed, village, List.of(), 200).log(),
                    claim).bubbleWithin(1, 30).isPresent();
            if (with && !without) {
                onlyWith++;
            }
            if (without && !with) {
                onlyWithout++;
            }
        }

        return new Measured(
                Estimate.proportion(bursts, 100),
                Estimate.ratePer(quietCounts, 500, 100),
                Estimate.mean(decays.stream().mapToDouble(Double::doubleValue).toArray()),
                Estimate.proportion(settled, 200),
                Estimate.mean(endPrices),
                Estimate.proportion(onlyWith, 200),
                Estimate.proportion(onlyWithout, 200));
    }

    private static List<Input> lie(long seed, Params village, Claim claim) {
        int planter = Run.execute(seed, village, List.of(), 1).finalState().gossipiestVillager().id();
        return List.of(new PlantRumor(1, claim, 1, planter));
    }

    private static MarketStats lied(long seed, Params village, Claim claim, int ticks) {
        return MarketStats.of(Run.execute(seed, village, lie(seed, village, claim), ticks).log(),
                claim);
    }

    private static double lastQuarterPrice(List<Event> log, Good good) {
        double sum = 0;
        int n = 0;
        for (Event event : log) {
            if (event instanceof MarketPriceSet price && price.item().equals(good.id())
                    && price.tick() > 525) {
                sum += price.price();
                n++;
            }
        }
        return n == 0 ? 100 : sum / n;
    }
}
