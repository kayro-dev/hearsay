package hearsay;

/**
 * Something a measurement has to reach, stated so that it cannot be a bare threshold.
 *
 * <p>Every target names the smallest sample it may be judged on, and judges only an
 * {@link Estimate}, which always carries its interval. There is no way to write "at least
 * 70%" and compare a number to it: the line has to come with how many observations it is
 * judged on, and it is judged against the interval, not the point.
 *
 * <p>And every band target says <em>which</em> claim it makes, because there are two and
 * mixing them up is how E39 went wrong:
 * <ul>
 *   <li>{@link Standard#DEMONSTRATED}: the whole interval lies inside the band. The data
 *       show the value is there. Use it for a claim — a gate a stage has to pass.</li>
 *   <li>{@link Standard#NOT_CONTRADICTED}: the interval reaches the band. The data do not
 *       rule it out. Use it for a guard — something that should fail only on a real
 *       change.</li>
 * </ul>
 * A comparison against another measurement on the same seeds — gold against diamond — asks
 * whether the two can be told apart at all, and is judged on the difference.
 */
public sealed interface Target permits Target.Band, Target.SameAs {

    /**
     * The fewest observations any target may be judged on. Below this an interval is too
     * wide to say anything, and a target that passes on it has passed on nothing.
     */
    int SMALLEST_SAMPLE = 10;

    enum Standard { DEMONSTRATED, NOT_CONTRADICTED }

    /** Pass or fail, and why, in words that belong in an experiment log. */
    record Verdict(String what, boolean passed, String why) {
        @Override
        public String toString() {
            return (passed ? "PASS  " : "FAIL  ") + what + " — " + why;
        }
    }

    String what();

    int atLeast();

    /** Lies in a band, to the stated standard, on at least this many observations. */
    record Band(String what, double low, double high, int atLeast, Standard standard)
            implements Target {

        public Band {
            requireSample(atLeast);
            if (!(low <= high)) {
                throw new IllegalArgumentException("a band from " + low + " to " + high);
            }
        }

        public Verdict judge(Estimate measured) {
            if (measured.n() < atLeast) {
                return tooFew(what, measured.n(), atLeast);
            }
            boolean passed = switch (standard) {
                case DEMONSTRATED -> low <= measured.low() && measured.high() <= high;
                case NOT_CONTRADICTED -> measured.high() >= low && measured.low() <= high;
            };
            return new Verdict(what, passed, measured + (passed ? " is " : " is not ")
                    + (standard == Standard.DEMONSTRATED ? "inside" : "compatible with")
                    + " [" + low + ", " + high + "]");
        }
    }

    /**
     * Indistinguishable from a reference measured on the same seeds: the interval on the
     * difference contains zero. Not a proof of equality — a small sample makes anything
     * indistinguishable — which is why the sample size is part of the target and a target
     * cannot be judged below it.
     */
    record SameAs(String what, int atLeast) implements Target {

        public SameAs {
            requireSample(atLeast);
        }

        public Verdict judge(Estimate measured, Estimate reference) {
            return judge(measured, reference, 1);
        }

        /**
         * Judged as one of several comparisons made together, sharing a 5% chance of a
         * false alarm between them. Five comparisons each judged at 95% will fail a good that
         * is identical by construction nearly a quarter of the time; five judged at 99% will
         * fail it about one time in twenty, which is what "95%" was supposed to mean for the
         * gate as a whole.
         *
         * @param comparisons how many comparisons the gate makes in all, this one included
         */
        public Verdict judge(Estimate measured, Estimate reference, int comparisons) {
            int fewer = Math.min(measured.n(), reference.n());
            if (fewer < atLeast) {
                return tooFew(what, fewer, atLeast);
            }
            Estimate difference = measured.minus(reference, Estimate.zFor(0.05 / comparisons));
            boolean passed = difference.low() <= 0 && 0 <= difference.high();
            return new Verdict(what, passed, "difference " + difference
                    + (passed ? " contains 0" : " excludes 0") + "; reference " + reference);
        }
    }

    static Band demonstrates(String what, double low, double high, int atLeast) {
        return new Band(what, low, high, atLeast, Standard.DEMONSTRATED);
    }

    static Band notContradicting(String what, double low, double high, int atLeast) {
        return new Band(what, low, high, atLeast, Standard.NOT_CONTRADICTED);
    }

    static SameAs sameAs(String what, int atLeast) {
        return new SameAs(what, atLeast);
    }

    private static void requireSample(int atLeast) {
        if (atLeast < SMALLEST_SAMPLE) {
            throw new IllegalArgumentException("a target judged on fewer than "
                    + SMALLEST_SAMPLE + " observations is not a target, was " + atLeast);
        }
    }

    private static Verdict tooFew(String what, int had, int needed) {
        return new Verdict(what, false,
                "refused to judge: " + had + " observations, the target needs " + needed);
    }
}
