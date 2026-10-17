package vn.pxvb.production.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.pxvb.production.domain.OeeCalculator.Formula;
import vn.pxvb.production.domain.OeeCalculator.Input;
import vn.pxvb.production.domain.OeeCalculator.PlannedTimeMode;
import vn.pxvb.production.domain.OeeCalculator.QualityMode;
import vn.pxvb.production.domain.OeeCalculator.Result;

/** Bộ số liệu chuẩn (golden) cho công thức OEE, SDD §8.3 và AC-PR-002. */
class OeeCalculatorTest {

    private static final Formula DEFAULT = new Formula(PlannedTimeMode.EXCLUDE_PLANNED_STOPS,
            QualityMode.ASSUME_100, false);
    private static final BigDecimal RATE_400 = BigDecimal.valueOf(400);

    private static Input input(int shift, int planned, int unplanned, long total, long good, BigDecimal rate) {
        return new Input(shift, planned, unplanned, total, good, rate);
    }

    @Test
    void acPr002_packerShift() {
        Result r = OeeCalculator.compute(DEFAULT, input(480, 30, 40, 150_000, 150_000, RATE_400));

        assertThat(r.plannedMinutes()).isEqualTo(450);
        assertThat(r.runMinutes()).isEqualTo(410);
        assertThat(r.availability().doubleValue()).isCloseTo(0.911111, within(1e-6));
        assertThat(r.performance().doubleValue()).isCloseTo(0.914634, within(1e-6));
        assertThat(r.quality().doubleValue()).isEqualTo(1.0);
        assertThat(r.oee().doubleValue()).isCloseTo(0.833333, within(1e-5));
    }

    @Test
    void fullShiftModeCountsPlannedStopsAsDowntime() {
        Formula full = new Formula(PlannedTimeMode.FULL_SHIFT, QualityMode.ASSUME_100, false);
        Result r = OeeCalculator.compute(full, input(480, 30, 40, 150_000, 150_000, RATE_400));

        assertThat(r.plannedMinutes()).isEqualTo(480);
        assertThat(r.runMinutes()).isEqualTo(410);
        assertThat(r.availability().doubleValue()).isCloseTo(410.0 / 480, within(1e-6));
    }

    @Test
    void performanceIsCappedOnlyWhenConfigured() {
        Input over = input(480, 0, 0, 200_000, 200_000, RATE_400); // lý thuyết 192.000
        assertThat(OeeCalculator.compute(DEFAULT, over).performance().doubleValue()).isGreaterThan(1.0);

        Formula capped = new Formula(PlannedTimeMode.EXCLUDE_PLANNED_STOPS, QualityMode.ASSUME_100, true);
        Result r = OeeCalculator.compute(capped, over);
        assertThat(r.performance().doubleValue()).isEqualTo(1.0);
        assertThat(r.oee().doubleValue()).isEqualTo(1.0);
    }

    @Test
    void nonconformanceReducesQualityUnlessAssumed100() {
        Formula fromNc = new Formula(PlannedTimeMode.EXCLUDE_PLANNED_STOPS, QualityMode.FROM_NC_RECORDS, false);
        Input in = input(480, 0, 0, 150_000, 145_000, RATE_400);

        assertThat(OeeCalculator.compute(fromNc, in).quality().doubleValue()).isCloseTo(0.966667, within(1e-6));
        assertThat(OeeCalculator.compute(DEFAULT, in).quality().doubleValue()).isEqualTo(1.0);
    }

    @Test
    void noPlannedTimeIsNotApplicable() {
        Result r = OeeCalculator.compute(DEFAULT, input(480, 480, 0, 0, 0, RATE_400));

        assertThat(r.plannedMinutes()).isZero();
        assertThat(r.oee()).isNull();
        assertThat(r.availability()).isNull();
    }

    @Test
    void missingRateMakesPerformanceAndOeeNotApplicableButKeepsAvailability() {
        Result r = OeeCalculator.compute(DEFAULT, input(480, 0, 60, 1000, 1000, null));

        assertThat(r.availability().doubleValue()).isCloseTo(0.875, within(1e-6));
        assertThat(r.performance()).isNull();
        assertThat(r.oee()).isNull();
    }

    @Test
    void zeroOutputGivesZeroOeeWithFullQuality() {
        Result r = OeeCalculator.compute(DEFAULT, input(480, 0, 0, 0, 0, RATE_400));

        assertThat(r.performance().doubleValue()).isZero();
        assertThat(r.quality().doubleValue()).isEqualTo(1.0);
        assertThat(r.oee().doubleValue()).isZero();
    }

    @Test
    void fullyStoppedMachineHasZeroOee() {
        Result r = OeeCalculator.compute(DEFAULT, input(480, 0, 480, 0, 0, RATE_400));

        assertThat(r.runMinutes()).isZero();
        assertThat(r.availability().doubleValue()).isZero();
        assertThat(r.oee().doubleValue()).isZero();
    }

    @Test
    void aggregationSumsNumeratorsAndDenominatorsInsteadOfAveragingPercentages() {
        Result shift1 = OeeCalculator.compute(DEFAULT, input(480, 30, 40, 150_000, 150_000, RATE_400));
        Result shift2 = OeeCalculator.compute(DEFAULT, input(240, 0, 0, 100_000, 100_000, RATE_400));

        Result day = OeeCalculator.aggregate(DEFAULT, List.of(shift1, shift2));

        assertThat(day.plannedMinutes()).isEqualTo(690);
        assertThat(day.runMinutes()).isEqualTo(650);
        assertThat(day.availability().doubleValue()).isCloseTo(650.0 / 690, within(1e-6));
        assertThat(day.performance().doubleValue()).isCloseTo(250_000.0 / (650 * 400), within(1e-6));
        double naiveAverage = (shift1.oee().doubleValue() + shift2.oee().doubleValue()) / 2;
        assertThat(day.oee().doubleValue()).isNotCloseTo(naiveAverage, within(1e-4));
    }

    @Test
    void aggregationWithUnknownTheoreticalQuantityIsNotApplicable() {
        Result known = OeeCalculator.compute(DEFAULT, input(480, 0, 0, 1000, 1000, RATE_400));
        Result unknown = OeeCalculator.compute(DEFAULT, input(480, 0, 0, 1000, 1000, null));

        Result day = OeeCalculator.aggregate(DEFAULT, List.of(known, unknown));

        assertThat(day.availability()).isNotNull();
        assertThat(day.oee()).isNull();
    }
}
