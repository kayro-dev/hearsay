package hearsay.experiments;

import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.Estimate;
import hearsay.MarketStats;
import hearsay.Params;
import hearsay.PlantRumor;
import hearsay.Run;
import hearsay.Simulation;
import hearsay.Target;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * How often CalibrationTest's three checks fail a model that has not changed, and how often
 * they catch one that has.
 *
 * <p>CalibrationTest runs on fixed seeds and never flickers between runs. What it can do is
 * fail when a change keeps the model's behaviour but re-rolls its dice, handing every seed a
 * fresh sample. So "known-good data" here is the real model on fresh seeds: thirty blocks at
 * each weight, pooled, then five thousand calibration runs of exactly the test's sizes drawn
 * from the pools and judged by the real {@link Target}s.
 *
 * <p>{@code ./gradlew :experiments:guard}
 */
public final class CalibrationGuard {

    private static final Claim SCARCE = new Claim(Simulation.DIAMOND, ClaimType.SCARCE);
    private static final double[] WEIGHTS = {0.28, 0.10, 0.15, 0.35, 0.45};
    private static final int BLOCKS = 30;
    private static final int SYNTHETIC = 5000;

    private CalibrationGuard() {
    }

    /** One weight's real runs: every lie's outcome, every quiet month, every lifetime. */
    private record Pool(List<Boolean> lies, List<Boolean> quietMonths, List<Integer> lifetimes) {}

    /** A way of judging the three checks, given the sizes it draws. */
    private interface Design {
        String name();
        int lieSeeds();
        boolean[] judge(int bursts, int quietBursts, int[] lifetimeCounts);
    }

    public static void main(String[] args) {
        List<Pool> pools = new ArrayList<>();
        for (double weight : WEIGHTS) {
            pools.add(poolAt(weight));
        }

        Target.Band quiet = Target.demonstrates("quiet, 30 days", 0.0, 0.03, 300);
        Target.Band lifetime = Target.demonstrates("quiet, 500 days", 0.0, 0.5, 60);
        List<Design> designs = List.of(
                design("A  as it was: burst demonstrated on 100", 100,
                        Target.demonstrates("burst", 0.25, 0.60, 100), quiet, lifetime),
                design("D  burst as a guard only (not contradicted)", 100,
                        Target.notContradicting("burst", 0.25, 0.60, 100), quiet, lifetime),
                design("E  burst demonstrated on 200", 200,
                        Target.demonstrates("burst", 0.25, 0.60, 200), quiet, lifetime),
                design("E  burst demonstrated on 300", 300,
                        Target.demonstrates("burst", 0.25, 0.60, 300), quiet, lifetime));

        System.out.printf("%-48s", "");
        for (double weight : WEIGHTS) {
            System.out.printf(Locale.ROOT, "%13s", String.format(Locale.ROOT, "%.2f %s", weight,
                    weight == 0.28 ? "good" : "bad"));
        }
        System.out.println();
        Random draws = new Random(20260923);
        for (Design design : designs) {
            System.out.printf("%-48s", design.name());
            for (Pool pool : pools) {
                int failed = 0;
                for (int run = 0; run < SYNTHETIC; run++) {
                    int bursts = count(pool.lies(), design.lieSeeds(), draws);
                    int quietBursts = count(pool.quietMonths(), 300, draws);
                    int[] lifetimes = new int[60];
                    for (int i = 0; i < 60; i++) {
                        lifetimes[i] = pool.lifetimes().get(draws.nextInt(pool.lifetimes().size()));
                    }
                    for (boolean passed : design.judge(bursts, quietBursts, lifetimes)) {
                        if (!passed) {
                            failed++;
                            break;
                        }
                    }
                }
                System.out.printf(Locale.ROOT, "%12.1f%%", 100.0 * failed / SYNTHETIC);
            }
            System.out.println();
        }
        System.out.println();
        System.out.println("Share of " + SYNTHETIC + " calibration runs that fail. Under 'good' it is the"
                + " false-failure rate; under 'bad' the power to catch a retune.");

        // What CalibrationTest's sizing test is pinned to, and which check catches what.
        System.out.println();
        System.out.println("Pooled rates, and each check alone at the sizes CalibrationTest uses (200 / 300 / 60):");
        Target.Band burst200 = Target.demonstrates("burst", 0.25, 0.60, 200);
        for (int w = 0; w < WEIGHTS.length; w++) {
            Pool pool = pools.get(w);
            double lieRate = pool.lies().stream().filter(b -> b).count() / (double) pool.lies().size();
            double monthRate = pool.quietMonths().stream().filter(b -> b).count()
                    / (double) pool.quietMonths().size();
            double lifeRate = pool.lifetimes().stream().mapToInt(Integer::intValue).sum() * 100.0
                    / (pool.lifetimes().size() * 500.0);
            int[] alone = new int[3];
            for (int run = 0; run < SYNTHETIC; run++) {
                int[] lifetimes = new int[60];
                for (int i = 0; i < 60; i++) {
                    lifetimes[i] = pool.lifetimes().get(draws.nextInt(pool.lifetimes().size()));
                }
                alone[0] += burst200.judge(Estimate.proportion(count(pool.lies(), 200, draws), 200))
                        .passed() ? 0 : 1;
                alone[1] += quiet.judge(Estimate.proportion(count(pool.quietMonths(), 300, draws), 300))
                        .passed() ? 0 : 1;
                alone[2] += lifetime.judge(Estimate.ratePer(lifetimes, 500, 100)).passed() ? 0 : 1;
            }
            System.out.printf(Locale.ROOT, "  %.2f  lie bursts %.3f, quiet month %.4f, quiet per 100 days %.3f"
                            + "  |  fails alone: burst %.1f%%, quiet month %.1f%%, lifetime %.1f%%%n",
                    WEIGHTS[w], lieRate, monthRate, lifeRate, 100.0 * alone[0] / SYNTHETIC,
                    100.0 * alone[1] / SYNTHETIC, 100.0 * alone[2] / SYNTHETIC);
        }
    }

    private static Design design(String name, int lieSeeds, Target.Band burst,
                                 Target.Band quiet, Target.Band lifetime) {
        return new Design() {
            public String name() { return name; }
            public int lieSeeds() { return lieSeeds; }
            public boolean[] judge(int bursts, int quietBursts, int[] lifetimeCounts) {
                return new boolean[] {
                        burst.judge(Estimate.proportion(bursts, lieSeeds)).passed(),
                        quiet.judge(Estimate.proportion(quietBursts, 300)).passed(),
                        lifetime.judge(Estimate.ratePer(lifetimeCounts, 500, 100)).passed()};
            }
        };
    }

    private static int count(List<Boolean> pool, int draws, Random random) {
        int hits = 0;
        for (int i = 0; i < draws; i++) {
            hits += pool.get(random.nextInt(pool.size())) ? 1 : 0;
        }
        return hits;
    }

    /** Thirty fresh blocks of the real model at this weight, far from the calibration seeds. */
    private static Pool poolAt(double weight) {
        Params params = Params.defaults().withObservationWeight(weight);
        List<Boolean> lies = new ArrayList<>();
        List<Boolean> months = new ArrayList<>();
        List<Integer> lifetimes = new ArrayList<>();
        for (int block = 0; block < BLOCKS; block++) {
            long base = 20000 + block * 1000L;
            for (int i = 0; i < 100; i++) {
                long seed = base + i;
                int planter = Run.execute(seed, params, List.of(), 1)
                        .finalState().gossipiestVillager().id();
                lies.add(MarketStats.of(Run.execute(seed, params,
                        List.of(new PlantRumor(1, SCARCE, 1, planter)), 200).log(), SCARCE)
                        .bubbleWithin(1, 30).isPresent());
            }
            for (int i = 0; i < 300; i++) {
                months.add(MarketStats.of(Run.execute(base + 100 + i, params, List.of(), 200)
                        .log(), SCARCE).bubbleWithin(1, 30).isPresent());
            }
            for (int i = 0; i < 60; i++) {
                lifetimes.add(MarketStats.of(Run.execute(base + 400 + i, params, List.of(), 2000)
                        .log(), SCARCE).bubbles().size());
            }
        }
        return new Pool(lies, months, lifetimes);
    }
}
