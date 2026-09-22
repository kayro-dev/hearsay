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
}
