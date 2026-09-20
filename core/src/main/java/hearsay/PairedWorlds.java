package hearsay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Many worlds, in pairs, to answer how likely the lie was to cause what happened.
 *
 * <p>One village's outcome could be luck. So the village is run up to the moment before the
 * lie, and then carried on many times under different futures. The trick is that the worlds
 * are <em>paired</em>: world 7 with the lie and world 7 without it are given the same branch
 * seed, so they face exactly the same future randomness. Villagers walk the same routes and
 * meet the same people in both, and the only difference inside a pair is the lie and what it
 * caused.
 *
 * <p>Comparing within pairs rather than between two piles of unrelated worlds takes almost
 * all the noise out of the answer, so far fewer worlds are needed to see anything. The
 * technique is known as common random numbers.
 *
 * <p>Every branch seed is derived from the run's own seed and the pair number, so naming the
 * seed and the number of pairs reproduces exactly these worlds.
 */
public final class PairedWorlds {

    /** One pair of worlds, alike in everything but the lie. */
    public record Pair(int index, long branchSeed, List<Event> withLie, List<Event> withoutLie) {

        public Pair {
            withLie = List.copyOf(withLie);
            withoutLie = List.copyOf(withoutLie);
        }

        public Comparison comparedOn(Claim claim) {
            return Comparison.of(withLie, withoutLie, claim);
        }

        /** Whether the price ran up and came back down in the world where the lie was told. */
        public boolean bubbledWithTheLie(Claim claim) {
            return bubbled(withLie, claim);
        }

        /** ...and whether it did so anyway in the world where it was not. */
        public boolean bubbledWithoutIt(Claim claim) {
            return bubbled(withoutLie, claim);
        }

        /** Whether the lie made the difference in this pair. */
        public boolean lieMadeTheDifference(Claim claim) {
            return bubbledWithTheLie(claim) && !bubbledWithoutIt(claim);
        }
    }

    private final long seed;
    private final int ticks;
    private final Input lie;
    private final List<Pair> pairs;
    private final List<Event> sharedHistory;

    private PairedWorlds(long seed, int ticks, Input lie, List<Pair> pairs,
                         List<Event> sharedHistory) {
        this.seed = seed;
        this.ticks = ticks;
        this.lie = lie;
        this.pairs = pairs;
        this.sharedHistory = sharedHistory;
    }

    /**
     * Runs the village to the tick before the lie, then carries it on {@code pairs} times
     * twice over: once where the lie is told and once where it is not.
     */
    public static PairedWorlds run(long seed, Params params, Input lie, int ticks, int pairs) {
        if (lie.tick() > ticks) {
            throw new IllegalArgumentException(
                    "the lie is told at tick " + lie.tick() + ", after the run ends at " + ticks);
        }
        int sharedTicks = (int) lie.tick() - 1;

        Simulation parent = new Simulation(seed, params, List.of());
        parent.run(sharedTicks);
        List<Event> sharedHistory = List.copyOf(parent.log());

        List<Pair> worlds = new ArrayList<>();
        for (int index = 0; index < pairs; index++) {
            long branchSeed = Seeds.branch(seed, index);
            worlds.add(new Pair(index, branchSeed,
                    carryOn(sharedHistory, branchSeed, params, List.of(lie), ticks - sharedTicks),
                    carryOn(sharedHistory, branchSeed, params, List.of(), ticks - sharedTicks)));
        }
        return new PairedWorlds(seed, ticks, lie, worlds, sharedHistory);
    }

    /**
     * One world's whole timeline: the shared history, then its own continuation. Each world
     * replays the history into a state of its own, so no two worlds share one.
     */
    private static List<Event> carryOn(List<Event> sharedHistory, long branchSeed, Params params,
                                       List<Input> inputs, int remainingTicks) {
        Simulation world = Simulation.resume(
                Simulation.replay(sharedHistory), branchSeed, params, inputs);
        world.run(remainingTicks);

        List<Event> whole = new ArrayList<>(sharedHistory);
        whole.addAll(world.log());
        return whole;
    }

    public long seed() { return seed; }
    public int ticks() { return ticks; }
    public Input lie() { return lie; }
    public List<Pair> pairs() { return Collections.unmodifiableList(pairs); }

    /** The history every world here shares, before the lie could have changed anything. */
    public List<Event> sharedHistory() { return Collections.unmodifiableList(sharedHistory); }

    /**
     * In how many pairs the lie made the difference: the price bubbled and burst in the
     * world where it was told, and did not in the world where it was not.
     */
    public int pairsWhereTheLieCausedABubble(Claim claim) {
        int caused = 0;
        for (Pair pair : pairs) {
            if (pair.lieMadeTheDifference(claim)) {
                caused++;
            }
        }
        return caused;
    }

    /** How many worlds bubbled with the lie, ignoring what their partners did. */
    public int worldsThatBubbledWithTheLie(Claim claim) {
        return countBubbles(true, claim);
    }

    /** ...and how many did so anyway, without it. */
    public int worldsThatBubbledWithoutIt(Claim claim) {
        return countBubbles(false, claim);
    }

    private int countBubbles(boolean withLie, Claim claim) {
        int count = 0;
        for (Pair pair : pairs) {
            if (withLie ? pair.bubbledWithTheLie(claim) : pair.bubbledWithoutIt(claim)) {
                count++;
            }
        }
        return count;
    }

    private static boolean bubbled(List<Event> log, Claim claim) {
        return MarketStats.of(log, claim).bubble().isPresent();
    }

    /** The mean of the per-pair differences in peak price: the lie's effect on the price. */
    public double meanPeakPriceDifference(Claim claim) {
        if (pairs.isEmpty()) {
            return 0;
        }
        double total = 0;
        for (Pair pair : pairs) {
            Comparison comparison = pair.comparedOn(claim);
            total += comparison.peakPriceWith() - comparison.peakPriceWithout();
        }
        return total / pairs.size();
    }

    /** The mean of the per-pair extra cost of a diamond a day. */
    public double meanExtraCost(Claim claim) {
        if (pairs.isEmpty()) {
            return 0;
        }
        double total = 0;
        for (Pair pair : pairs) {
            total += pair.comparedOn(claim).extraCostOfADiamondEachMarketDay();
        }
        return total / pairs.size();
    }
}
