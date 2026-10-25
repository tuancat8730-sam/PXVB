package vn.pxvb.masterdata.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Luật phân cấp lỗi A/B/C (FR-MD-012): cỡ mẫu theo cách đo và miền giá trị ngưỡng. */
class DefectGradeRuleRulesTest {

    @Test
    void acceptsCountRuleWithPositiveSampleSize() {
        assertThat(DefectGradeRules.violations("COUNT_IN_SAMPLE", new BigDecimal("3"), 50)).isEmpty();
    }

    @Test
    void rejectsCountRuleWithoutSampleSize() {
        assertThat(DefectGradeRules.violations("COUNT_IN_SAMPLE", new BigDecimal("3"), null))
                .anyMatch(m -> m.contains("Cỡ mẫu"));
    }

    @Test
    void rejectsNegativeThreshold() {
        assertThat(DefectGradeRules.violations("RATE_PCT", new BigDecimal("-1"), null))
                .anyMatch(m -> m.contains("không được âm"));
    }

    @Test
    void rejectsRatePercentAboveOneHundred() {
        assertThat(DefectGradeRules.violations("RATE_PCT", new BigDecimal("120"), null))
                .anyMatch(m -> m.contains("từ 0 đến 100"));
    }

    @Test
    void acceptsRatePercentWithinRange() {
        assertThat(DefectGradeRules.violations("RATE_PCT", new BigDecimal("2.5"), null)).isEmpty();
    }
}
