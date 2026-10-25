package vn.pxvb.masterdata.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Kiểm tra một luật phân cấp lỗi A/B/C đứng riêng (FR-MD-012). Kiểm chồng lấn hiệu lực nằm ở truy vấn DB riêng. */
final class DefectGradeRules {

    private static final BigDecimal MAX_PERCENT = BigDecimal.valueOf(100);

    private DefectGradeRules() {
    }

    static List<String> violations(String metric, BigDecimal threshold, Integer sampleSize) {
        List<String> out = new ArrayList<>();
        if (threshold == null) {
            return out;
        }
        if (threshold.signum() < 0) {
            out.add("Ngưỡng không được âm");
        }
        if ("RATE_PCT".equals(metric) && threshold.compareTo(MAX_PERCENT) > 0) {
            out.add("Tỷ lệ phần trăm phải từ 0 đến 100");
        }
        if ("COUNT_IN_SAMPLE".equals(metric) && (sampleSize == null || sampleSize <= 0)) {
            out.add("Cách đếm trong mẫu cần Cỡ mẫu lớn hơn 0");
        }
        return out;
    }
}
