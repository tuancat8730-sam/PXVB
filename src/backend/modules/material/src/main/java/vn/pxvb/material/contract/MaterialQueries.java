package vn.pxvb.material.contract;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Truy vấn tiêu hao NVL của MT cho module khác (RP dựng fact NVL theo phiên ca, ADR-0001). */
public interface MaterialQueries {

    /**
     * Tiêu hao NVL đã tính của phiên ca theo (máy tiêu thụ × NVL), lấy nguyên từ kết quả đã lưu của MT.
     * Rỗng nếu phiên ca chưa có dòng tiêu hao nào.
     */
    List<ConsumptionLine> consumption(UUID sessionId);

    /**
     * @param wastePct {@code null} khi định mức bằng 0 (hao hụt ngoài định mức)
     * @param flag     OK | WARN | CRIT
     * @param status   DRAFT | CONFIRMED
     */
    record ConsumptionLine(UUID machineId, UUID materialId, BigDecimal openingQty, BigDecimal issuedQty,
                           BigDecimal returnedQty, BigDecimal closingQty, boolean counted,
                           BigDecimal actualConsumed, BigDecimal theoreticalConsumed, BigDecimal wasteQty,
                           BigDecimal wastePct, BigDecimal thresholdPct, String flag, String explanation,
                           String status) {
    }
}
