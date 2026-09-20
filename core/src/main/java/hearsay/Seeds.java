package hearsay;

/** Deriving one seed from another, so a branching experiment stays reproducible. */
public final class Seeds {

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private Seeds() {
    }

    /**
     * The seed for one branch of a run. Derived from the run's own seed and the branch
     * number, so naming the original seed and the number of branches is enough to produce
     * exactly the same set of worlds again.
     *
     * <p>Spread by the SplitMix64 mixing function rather than by adding the index, so
     * neighbouring branches do not move in step with each other.
     */
    public static long branch(long seed, int index) {
        return mix(seed + GOLDEN_GAMMA * (index + 1L));
    }

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
