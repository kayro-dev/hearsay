package hearsay.experiments;

import hearsay.Belief;
import hearsay.Claim;
import hearsay.ClaimType;
import hearsay.Estimate;
import hearsay.Event;
import hearsay.Good;
import hearsay.Params;
import hearsay.PlantRumor;
import hearsay.PriceObserved;
import hearsay.RumorTold;
import hearsay.Run;
import hearsay.WorldState;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Broadcast against gossip, with and without the level gate (E49).
 *
 * <p>E47 found a glut's rebound, read off the market by everyone at once, convincing more
 * villagers than a lie spread mouth to mouth: fifteen against six at the 90th percentile.
 * The gate removed that rebound, so the question is whether anything of the channel's
 * advantage is left. A lie spreads through both channels, so every villager who comes to
 * believe it is attributed to the event that tipped them over: a telling, or a reading of
 * the price.
 *
 * <pre>./gradlew :experiments:channels</pre>
 */
public final class Channels {

    private static final Claim SCARCE = new Claim(Good.DIAMOND.id(), ClaimType.SCARCE);
    private static final double BELIEVES = 0.5;
    private static final int RUNS = 200;

    private Channels() {
    }

    public static void main(String[] args) {
        for (double gate : new double[] {0.0, 1.0}) {
            Params params = Params.defaults().withLevelGate(gate);
            int[] onsets = new int[2]; // 0 gossip, 1 market
            int[] exposures = new int[2];
            int[][] crowd = new int[2][RUNS];
            int[] firstFiveBy = new int[RUNS];
            for (int i = 0; i < RUNS; i++) {
                long seed = 1001 + i;
                int planter = Run.execute(seed, params, List.of(), 1).finalState()
                        .gossipiestVillager().id();
                Run run = Run.execute(seed, params,
                        List.of(new PlantRumor(1, SCARCE, 1, planter)), 200);
                WorldState world = new WorldState();
                Map<Long, int[]> perTick = new TreeMap<>();
                for (Event event : run.log()) {
                    int channel = -1;
                    int villager = -1;
                    if (event instanceof RumorTold told) {
                        channel = 0;
                        villager = told.listenerId();
                    } else if (event instanceof PriceObserved read && read.claim().equals(SCARCE)) {
                        channel = 1;
                        villager = read.villagerId();
                    }
                    double before = channel < 0 ? 0 : confidence(world, villager);
                    world.apply(event);
                    if (channel < 0) {
                        continue;
                    }
                    if (channel == 0 && !world.rumor(((RumorTold) event).keptRumorId()).claim()
                            .equals(SCARCE)) {
                        continue;
                    }
                    exposures[channel]++;
                    if (before < BELIEVES && confidence(world, villager) >= BELIEVES) {
                        onsets[channel]++;
                        perTick.computeIfAbsent(event.tick(), t -> new int[2])[channel]++;
                    }
                }
                for (int[] tick : perTick.values()) {
                    crowd[0][i] = Math.max(crowd[0][i], tick[0]);
                    crowd[1][i] = Math.max(crowd[1][i], tick[1]);
                }
            }
            System.out.println("== levelGate " + gate + ", a lie at tick 1, " + RUNS
                    + " villages over 50 days (seeds 1001-" + (1000 + RUNS) + ")");
            report("gossip (a telling)", onsets[0], exposures[0], crowd[0]);
            report("broadcast (reading the price)", onsets[1], exposures[1], crowd[1]);
            System.out.println();
        }
    }

    private static double confidence(WorldState world, int villager) {
        if (world.villagers().isEmpty()) {
            return 0;
        }
        Belief held = world.villager(villager).belief(SCARCE);
        return held == null ? 0 : held.confidence();
    }

    private static void report(String channel, int onsets, int exposures, int[] crowd) {
        int[] sorted = crowd.clone();
        Arrays.sort(sorted);
        long runsWithAny = Arrays.stream(crowd).filter(c -> c > 0).count();
        System.out.printf(Locale.ROOT,
                "  %-30s tipped into believing: %4d  |  exposures %6d, each converting %s  |  "
                        + "most converted in one tick: median %d, 90th %d, max %d; in %d runs at all%n",
                channel, onsets, exposures, Estimate.proportion(onsets, Math.max(1, exposures)),
                sorted[RUNS / 2], sorted[(int) (RUNS * 0.9)], sorted[RUNS - 1], runsWithAny);
    }
}
