package hearsay;

import java.util.Locale;

/**
 * A measured number that cannot exist without its sample size and its interval.
 *
 * <p>Built only from data, by the factories below, so there is no way to hand a
 * {@link Target} a bare figure. That is the point: E30 published a quiet-village rate from a
 * hundred seeds as though it were a law, and E39 drew a "70% settled" line under a sixty-seed
 * estimate that moved by twenty points between samples. Both were numbers that had lost their
 * uncertainty somewhere between the measurement and the claim.
 *
 * <p>Intervals are 95%, and computed rather than resampled, so the same data always give the
 * same interval and a test built on one never flickers.
 */
public final class Estimate {

    private static final double Z = 1.96;

    private final double value;
    private final double low;
    private final double high;
    private final int n;

    private Estimate(double value, double low, double high, int n) {
        if (n < 1) {
            throw new IllegalArgumentException("an estimate needs at least one observation");
        }
        this.value = value;
        this.low = low;
        this.high = high;
        this.n = n;
    }

    /**
     * The share of trials that succeeded, with a Wilson interval.
     *
     * <p>Wilson rather than the textbook normal interval, because the normal one collapses
     * to nothing at zero successes — it would say that 0 quiet bursts in 300 villages proves
     * the rate is exactly 0%, which is the claim E30 had to withdraw.
     */
    public static Estimate proportion(int successes, int trials) {
        if (successes < 0 || successes > trials) {
            throw new IllegalArgumentException(successes + " successes in " + trials + " trials");
        }
        double p = successes / (double) trials;
        double z2 = Z * Z;
        double centre = (p + z2 / (2 * trials)) / (1 + z2 / trials);
        double margin = Z * Math.sqrt(p * (1 - p) / trials + z2 / (4.0 * trials * trials))
                / (1 + z2 / trials);
        return new Estimate(p, Math.max(0, centre - margin), Math.min(1, centre + margin), trials);
    }

    /** The mean of some measurements, with a normal interval on the mean. */
    public static Estimate mean(double... values) {
        if (values.length < 2) {
            throw new IllegalArgumentException("a mean of fewer than two things has no spread");
        }
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        double mean = sum / values.length;
        double squares = 0;
        for (double v : values) {
            squares += (v - mean) * (v - mean);
        }
        double se = Math.sqrt(squares / (values.length - 1) / values.length);
        return new Estimate(mean, mean - Z * se, mean + Z * se, values.length);
    }

    /**
     * How often something happens per unit of exposure, from a count in each run — bursts per
     * hundred village-days, say. The interval is on the mean count per run, so a run that
     * burst five times counts as one unusual run rather than five independent events.
     */
    public static Estimate ratePer(int[] countPerRun, double exposurePerRun, double per) {
        double[] rates = new double[countPerRun.length];
        for (int i = 0; i < countPerRun.length; i++) {
            rates[i] = countPerRun[i] * per / exposurePerRun;
        }
        Estimate m = mean(rates);
        return new Estimate(m.value, Math.max(0, m.low), m.high, m.n);
    }

    /**
     * This minus another, as an estimate of the difference, for comparing two goods measured
     * on the same seeds. The two are treated as independent, which they are: each good draws
     * from its own streams.
     */
    public Estimate minus(Estimate other) {
        double se = Math.hypot(halfWidth() / Z, other.halfWidth() / Z);
        double diff = value - other.value;
        return new Estimate(diff, diff - Z * se, diff + Z * se, Math.min(n, other.n));
    }

    public double value() { return value; }
    public double low() { return low; }
    public double high() { return high; }
    public int n() { return n; }

    private double halfWidth() {
        return (high - low) / 2;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%.3f [%.3f, %.3f] n=%d", value, low, high, n);
    }
}
