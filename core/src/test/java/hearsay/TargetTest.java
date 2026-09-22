package hearsay;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Targets that cannot be a bare line, judged on intervals that cannot be left off. */
class TargetTest {

    @Test
    void aProportionCarriesItsWilsonInterval() {
        // 43 of 100: the lie's thirty-day burst rate on the calibration seeds.
        Estimate bursts = Estimate.proportion(43, 100);
        assertEquals(0.43, bursts.value(), 1e-12);
        assertEquals(0.337, bursts.low(), 0.001);
        assertEquals(0.528, bursts.high(), 0.001);
    }

    @Test
    void noneObservedIsNotProofOfNone() {
        // The claim E30 had to withdraw. Nothing in 300 villages still leaves room for about
        // one in eighty, and the interval has to say so rather than collapse to zero.
        Estimate quiet = Estimate.proportion(0, 300);
        assertEquals(0, quiet.low(), 1e-12);
        assertTrue(quiet.high() > 0.01, "0 of 300 should still allow about 1.3%, was " + quiet);
    }

    @Test
    void theSameNumberPassesOrFailsDependingOnWhichClaimIsMade() {
        // E39 in one test: gold settled in 66% of sixty runs against a "70%" line.
        Estimate gold = Estimate.proportion(40, 60);

        assertFalse(Target.demonstrates("settled", 0.70, 1.0, 60).judge(gold).passed(),
                "66% of sixty does not demonstrate 70% or better");
        assertTrue(Target.notContradicting("settled", 0.70, 1.0, 60).judge(gold).passed(),
                "but nor does it rule it out");
    }

    @Test
    void aPointJustOverTheLineDemonstratesNothing() {
        // The case a point comparison gets wrong and an interval gets right: 72% of sixty
        // clears a 70% line, and its interval runs down to about 60%. Nothing has been shown.
        Estimate justOver = Estimate.proportion(43, 60);
        assertTrue(justOver.value() > 0.70, "the fixture should sit just over the line");

        assertFalse(Target.demonstrates("settled", 0.70, 1.0, 60).judge(justOver).passed(),
                "a point over the line is not a demonstration when the interval is not");
        assertTrue(Target.notContradicting("settled", 0.70, 1.0, 60).judge(justOver).passed());
    }

    @Test
    void aBareLineFailsTheVeryThingItWasCopiedFrom() {
        // Diamond itself, on seeds 1151-1200, against the line drawn under diamond's own
        // sixty-seed estimate. A line with no interval is a line diamond can fail.
        Estimate diamondOnAnotherBlock = Estimate.proportion(31, 50);
        assertFalse(Target.demonstrates("settled", 0.70, 1.0, 50)
                .judge(diamondOnAnotherBlock).passed());
    }

    @Test
    void goldCannotBeToldApartFromDiamondOnTheSameSeeds() {
        // The comparison E39 settled on: two hundred seeds each, 69.5% against 72.5%.
        Estimate gold = Estimate.proportion(139, 200);
        Estimate diamond = Estimate.proportion(145, 200);

        Target.Verdict verdict = Target.sameAs("settled", 200).judge(gold, diamond);
        assertTrue(verdict.passed(), verdict.toString());
    }

    @Test
    void aRealDifferenceIsCaught() {
        // Something genuinely unlike diamond must not slip through as "the same".
        Estimate broken = Estimate.proportion(60, 200);
        Estimate diamond = Estimate.proportion(145, 200);

        assertFalse(Target.sameAs("settled", 200).judge(broken, diamond).passed());
    }

    @Test
    void aTargetRefusesToBeJudgedOnTooLittle() {
        Target.Verdict thin = Target.demonstrates("settled", 0.0, 1.0, 100)
                .judge(Estimate.proportion(9, 10));

        assertFalse(thin.passed(), "ten runs cannot meet a target that asks for a hundred");
        assertTrue(thin.why().contains("refused"), thin.why());
    }

    @Test
    void aTargetOnAHandfulCannotBeWrittenAtAll() {
        assertThrows(IllegalArgumentException.class,
                () -> Target.demonstrates("settled", 0.7, 1.0, 5),
                "a target on five observations is not a target");
        assertThrows(IllegalArgumentException.class, () -> Target.sameAs("settled", 3));
    }

    @Test
    void aRateCountsUnusualRunsAsRunsNotAsEvents() {
        // Bursts per 100 village-days from per-run counts: 60 runs of 500 days.
        int[] counts = new int[60];
        counts[0] = 5; // one strange village
        counts[1] = 1;
        Estimate rate = Estimate.ratePer(counts, 500, 100);

        assertEquals(0.02, rate.value(), 1e-12); // six bursts in 30,000 village-days
        assertTrue(rate.low() >= 0, "a rate cannot be negative");
        assertEquals(60, rate.n());
    }

    @Test
    void theQuantileIsTheOneEveryoneKnows() {
        assertEquals(1.960, Estimate.zFor(0.05), 0.001);
        assertEquals(2.576, Estimate.zFor(0.01), 0.001);
        assertEquals(Estimate.zFor(0.01), Estimate.zFor(0.05 / 5), 1e-12,
                "five comparisons sharing 5% is each at 1%");
    }

    /** How often a gate of five comparisons fails a good identical to its reference. */
    private static double falseFailureRate(int judgedAsOneOf) {
        java.util.Random draws = new java.util.Random(20260922);
        int gates = 2000;
        int failed = 0;
        for (int gate = 0; gate < gates; gate++) {
            boolean anyFailed = false;
            for (int comparison = 0; comparison < 5; comparison++) {
                // Both drawn from exactly the same truth: 50%, two hundred trials each.
                int a = 0;
                int b = 0;
                for (int trial = 0; trial < 200; trial++) {
                    a += draws.nextBoolean() ? 1 : 0;
                    b += draws.nextBoolean() ? 1 : 0;
                }
                Target.Verdict verdict = Target.sameAs("identical", 200).judge(
                        Estimate.proportion(a, 200), Estimate.proportion(b, 200), judgedAsOneOf);
                anyFailed |= !verdict.passed();
            }
            failed += anyFailed ? 1 : 0;
        }
        return failed / (double) gates;
    }

    @Test
    void fiveComparisonsAt95EachFailAnIdenticalGoodAboutAQuarterOfTheTime() {
        // The defect E42 found in the gate built for E40: a good that is diamond's market with
        // different dice, compared on five measures at 95% each, fails one of them by chance
        // far more often than one time in twenty.
        double uncorrected = falseFailureRate(1);
        assertTrue(uncorrected > 0.15 && uncorrected < 0.30,
                "five uncorrected comparisons should fail an identical good about 23% of the "
                        + "time, was " + uncorrected);
    }

    @Test
    void sharingTheConfidenceBringsThatBackToOneInTwenty() {
        double corrected = falseFailureRate(5);
        assertTrue(corrected < 0.08,
                "judged as one of five, an identical good should fail about 5% of the time, was "
                        + corrected);
    }

    @Test
    void sharingTheConfidenceStillCatchesARealDifference() {
        // The correction must not buy fewer false alarms by going blind.
        Estimate broken = Estimate.proportion(60, 200);
        Estimate diamond = Estimate.proportion(145, 200);
        assertFalse(Target.sameAs("settled", 200).judge(broken, diamond, 5).passed());
    }
}
