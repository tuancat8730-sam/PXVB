package vn.pxvb.material.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** BR-MT-001: chia nhu cầu NVL của ca vào các khung giờ theo {@code SharePct}; phần dư dồn vào khung cuối. */
public final class SlipAllocator {

    private SlipAllocator() {
    }

    /** {@code PlannedQty = QtyPer1000Packs × (PlannedPacks / 1000)}. */
    public static BigDecimal demand(BigDecimal qtyPer1000Packs, long plannedPacks) {
        return qtyPer1000Packs.multiply(BigDecimal.valueOf(plannedPacks)).divide(BigDecimal.valueOf(1000), 6,
                RoundingMode.HALF_UP);
    }

    /**
     * @param shares tỷ lệ (%) theo thứ tự khung; tổng không nhất thiết 100 — nhu cầu được chia theo tỷ trọng
     * @return số lượng từng khung, làm tròn {@code scale} chữ số; tổng đúng bằng {@code total} làm tròn
     */
    public static List<BigDecimal> allocate(BigDecimal total, List<BigDecimal> shares, int scale) {
        BigDecimal sum = shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal target = total.setScale(scale, RoundingMode.HALF_UP);
        List<BigDecimal> out = new ArrayList<>(shares.size());
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < shares.size(); i++) {
            BigDecimal part;
            if (i == shares.size() - 1) {
                part = target.subtract(allocated);
            } else if (sum.signum() == 0) {
                part = BigDecimal.ZERO.setScale(scale);
            } else {
                part = total.multiply(shares.get(i)).divide(sum, scale, RoundingMode.HALF_UP);
            }
            out.add(part);
            allocated = allocated.add(part);
        }
        return out;
    }
}
