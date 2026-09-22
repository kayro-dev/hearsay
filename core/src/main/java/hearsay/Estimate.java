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
        return minus(other, Z);
    }

    /**
     * The difference with an interval at a stated width, for when one comparison is one of
     * several and the confidence has to be shared between them. See {@link #zFor}.
     */
    public Estimate minus(Estimate other, double z) {
        double se = Math.hypot(halfWidth() / Z, other.halfWidth() / Z);
        double diff = value - other.value;
        return new Estimate(diff, diff - z * se, diff + z * se, Math.min(n, other.n));
    }

    /**
     * How many standard errors wide an interval must be so that it misses the truth with
     * the given chance, split evenly between the two tails.
     *
     * <p>{@code zFor(0.05)} is 1.96, the usual 95%. {@code zFor(0.05 / 5)} is 2.58, which is
     * what five comparisons have to use between them if together they are to be wrong only
     * one time in twenty. Judge five things at 95% each and something passes that should
     * not, or fails that should not, nearly a quarter of the time.
     *
     * <p>Acklam's rational approximation to the normal quantile, accurate far beyond what
     * any interval here needs, and deterministic.
     */
    public static double zFor(double twoSidedChance) {
        if (!(twoSidedChance > 0 && twoSidedChance < 1)) {
            throw new IllegalArgumentException("a chance between 0 and 1, was " + twoSidedChance);
        }
        double p = 1 - twoSidedChance / 2;
        double[] a = {-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02,
                1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00};
        double[] b = {-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02,
                6.680131188771972e+01, -1.328068155288572e+01};
        double[] c = {-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00,
                -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00};
        double[] d = {7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00,
                3.754408661907416e+00};
        double high = 1 - 0.02425;
        if (p > high) {
            double q = Math.sqrt(-2 * Math.log(1 - p));
            return -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5])
                    / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
        }
        double q = p - 0.5;
        double r = q * q;
        return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q
                / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1);
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
