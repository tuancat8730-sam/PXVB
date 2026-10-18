package vn.pxvb.production.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Truy vấn phiên ca/sản lượng của PR cho module khác (MT tính tiêu hao, QC, TX, RP…). */
public interface ProductionQueries {

    record SessionRef(UUID id, LocalDate businessDate, String shiftCode, String status, Instant plannedStart,
                      Instant plannedEnd) {
    }

    Optional<SessionRef> session(UUID sessionId);

    Optional<SessionRef> session(LocalDate businessDate, String shiftCode);

    List<SessionRef> openSessions();

    /** Sản lượng (bao) theo mác ghi nhận trên máy bao trong phiên ca. */
    Map<UUID, Long> packsByProduct(UUID sessionId, UUID packerMachineId);

    /** Các phiên ca có {@code businessDate} trong [from, to], sắp theo ngày rồi mã ca. */
    List<SessionRef> sessionsBetween(LocalDate from, LocalDate to);

    /**
     * Dữ liệu cộng dồn được của cả phiên ca cho báo cáo (RP, ADR-0001): mỗi máy có kết quả OEE kèm sản lượng theo
     * segment mác và dừng máy theo lý do. Số OEE lấy nguyên từ kết quả đã lưu, không tính lại ở đây. Rỗng nếu
     * không có phiên ca.
     * <p>
     * Mỗi máy nhất quán tại một mốc: lúc OEE được tính ({@code computedAt}) hoặc lúc kết ca; sản lượng theo segment và
     * dừng máy cũng lấy tại mốc đó. Chỉ có máy đã có kết quả OEE; máy chưa có kết quả (vd ca vừa mở) chưa xuất hiện
     * cho tới lần tính đầu, khi đó phát {@code pr.oee.computed}. Người gọi coi máy vắng mặt là "chưa tính", không phải 0.
     */
    Optional<ShiftFacts> shiftFacts(UUID sessionId);

    record ShiftFacts(UUID sessionId, LocalDate businessDate, String shiftCode, String status, List<RunFact> runs) {
    }

    /**
     * Một máy trong phiên ca. {@code plannedMinutes}/{@code runMinutes} theo chế độ thời gian kế hoạch của công thức
     * {@code formulaId}; {@code plannedStopMinutes}/{@code unplannedStopMinutes} là phút dừng thực tế (sự kiện
     * đang mở tính đến lúc kết ca hoặc hiện tại). Tỷ lệ {@code null} nghĩa là N/A (vd chưa có công suất lý thuyết).
     * {@code performanceCapped}: công thức áp trần hiệu năng 100% từng máy/ca (L42/L57).
     */
    record RunFact(UUID machineId, String unit, Long plannedPacks, int plannedMinutes, int runMinutes,
                   int plannedStopMinutes, int unplannedStopMinutes, long totalQty, long goodQty,
                   BigDecimal theoreticalQty, BigDecimal availability, BigDecimal performance, BigDecimal quality,
                   BigDecimal oee, boolean provisional, UUID formulaId, boolean performanceCapped,
                   Instant computedAt, List<SegmentFact> segments, List<StopFact> stops) {
    }

    /** Sản lượng của một segment mác (đổi mác tạo segment mới) trên một máy. */
    record SegmentFact(int segmentNo, UUID productId, long qty, int entries) {
    }

    /** Thời gian dừng gộp theo mã lý do và loại (kế hoạch/ngoài kế hoạch) trên một máy. */
    record StopFact(String reasonCode, boolean planned, long durationSeconds, int events) {
    }
}
