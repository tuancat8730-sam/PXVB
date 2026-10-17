package vn.pxvb.material.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.pxvb.material.domain.ConsumptionCalculator.Flag;
import vn.pxvb.material.domain.ConsumptionCalculator.Input;

/** Số liệu chuẩn cho phân bổ phiếu (AC-MT-001) và tiêu hao/hao hụt (AC-MT-004). */
class MaterialCalcTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void acMt001_fourSlotsOfTwentyFivePercent() {
        BigDecimal demand = SlipAllocator.demand(d("2.5"), 20_000);
        assertThat(demand).isEqualByComparingTo("50");

        List<BigDecimal> parts = SlipAllocator.allocate(demand, List.of(d("25"), d("25"), d("25"), d("25")), 3);

        assertThat(parts).allSatisfy(p -> assertThat(p).isEqualByComparingTo("12.5"));
    }

    @Test
    void remainderGoesToLastSlotSoTotalIsExact() {
        List<BigDecimal> parts = SlipAllocator.allocate(d("10"), List.of(d("25"), d("25"), d("25"), d("25")), 0);
        // 2,5 → 3 làm tròn HALF_UP cho 3 khung đầu; khung cuối nhận phần dư
        assertThat(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("10");
        assertThat(parts.get(3)).isEqualByComparingTo("1");
    }

    @Test
    void acMt004_countedActualFivePercentAboveTheoreticalIsWarned() {
        // Đầu 20, cấp 100, hoàn 0, tồn cuối đếm 15 → thực tế 105; lý thuyết 100 → hao hụt 5% > ngưỡng 3%
        var r = ConsumptionCalculator.compute(new Input(d("20"), d("100"), d("0"), d("15"), d("100"), d("3"), false));

        assertThat(r.counted()).isTrue();
        assertThat(r.actualConsumed()).isEqualByComparingTo("105");
        assertThat(r.wastePct()).isEqualByComparingTo("5");
        assertThat(r.flag()).isEqualTo(Flag.WARN);
    }

    @Test
    void wasteMoreThanTwiceThresholdIsCritical() {
        var r = ConsumptionCalculator.compute(new Input(d("0"), d("110"), d("0"), d("0"), d("100"), d("3"), false));
        assertThat(r.wastePct()).isEqualByComparingTo("10");
        assertThat(r.flag()).isEqualTo(Flag.CRIT);
    }

    @Test
    void withinThresholdIsOk() {
        var r = ConsumptionCalculator.compute(new Input(d("0"), d("102"), d("0"), d("0"), d("100"), d("3"), false));
        assertThat(r.flag()).isEqualTo(Flag.OK);
    }

    @Test
    void withoutCountWasteIsOnlyDetectedByOverIssueAfterShiftEnds() {
        Input mid = new Input(d("5"), d("120"), d("0"), null, d("100"), d("3"), false);
        assertThat(ConsumptionCalculator.compute(mid).flag()).isEqualTo(Flag.OK); // giữa ca: chưa đánh giá

        var ended = ConsumptionCalculator.compute(new Input(d("5"), d("120"), d("0"), null, d("100"), d("3"), true));
        assertThat(ended.counted()).isFalse();
        assertThat(ended.closing()).isEqualByComparingTo("25"); // 5 + 120 - 100
        assertThat(ended.wasteQty()).isEqualByComparingTo("15"); // 120 - 5 - 100
        assertThat(ended.flag()).isEqualTo(Flag.CRIT);
    }

    @Test
    void returnsReduceActualConsumption() {
        var r = ConsumptionCalculator.compute(new Input(d("0"), d("100"), d("10"), d("0"), d("90"), d("3"), false));
        assertThat(r.actualConsumed()).isEqualByComparingTo("90");
        assertThat(r.flag()).isEqualTo(Flag.OK);
    }

    @Test
    void materialOutsideNormWithWasteIsWarned() {
        var r = ConsumptionCalculator.compute(new Input(d("0"), d("5"), d("0"), d("0"), d("0"), d("3"), false));
        assertThat(r.wastePct()).isNull();
        assertThat(r.flag()).isEqualTo(Flag.WARN);
    }
}
