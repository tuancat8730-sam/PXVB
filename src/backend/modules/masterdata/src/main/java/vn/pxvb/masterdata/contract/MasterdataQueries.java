package vn.pxvb.masterdata.contract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Hợp đồng truy vấn masterdata cho các phân hệ khác (PL, MT, PR, QC, …). */
public interface MasterdataQueries {

    record ThresholdValue(BigDecimal value, String unit, String scopeType, String level) {
    }

    record NormLine(UUID materialId, String materialCode, BigDecimal qtyPer1000Packs, String uom,
                    String consumerType) {
    }

    record Norm(UUID normId, UUID productId, int versionNo, LocalDate effectiveFrom, List<NormLine> lines) {
    }

    record PairingLine(String shiftCode, UUID makerMachineId, UUID packerMachineId, BigDecimal feedRatio) {
    }

    /**
     * Ngưỡng hiệu lực tại {@code asOf}, ưu tiên phạm vi hẹp nhất: MACHINE &gt; PRODUCT &gt;
     * MODEL/MATERIAL_GROUP/STAMP_TYPE &gt; GLOBAL (BR-MD-008).
     */
    Optional<ThresholdValue> resolveThreshold(String metric, UUID machineId, UUID productId, String materialGroup,
                                              String stampTypeCode, String level, LocalDate asOf);

    /** Định mức hiệu lực của sản phẩm tại {@code asOf} (lệnh SX tham chiếu phiên bản tại thời điểm xác nhận). */
    Optional<Norm> activeNorm(UUID productId, LocalDate asOf);

    /** Các cặp vấn–bao hiệu lực của ca tại ngày; {@code shiftCode} khớp chính xác hoặc dòng {@code ALL}. */
    List<PairingLine> resolvePairing(LocalDate date, String shiftCode);

    /** {@code true} nếu mã lý do đang hoạt động trong nhóm (vd nhóm {@code ALERT_CAUSE}). */
    boolean reasonExists(String group, String code);

    record MachineInfo(UUID id, String code, String machineType, String status, boolean active, String model) {
    }

    record ProductInfo(UUID id, String code, String name, boolean active, Integer rodsPerPack) {
    }

    record EmployeeInfo(UUID id, String code, String fullName, boolean active) {
    }

    java.util.Map<UUID, MachineInfo> machines(java.util.Collection<UUID> ids);

    java.util.Map<UUID, ProductInfo> products(java.util.Collection<UUID> ids);

    java.util.Map<UUID, EmployeeInfo> employees(java.util.Collection<UUID> ids);

    Optional<UUID> productIdByCode(String code);

    /** Toàn bộ máy đang hoạt động (dùng khi mở phiên ca: một dòng cho mỗi máy). */
    List<MachineInfo> activeMachines();

    record ShiftWindow(String code, java.time.LocalTime start, java.time.LocalTime end, boolean crossesMidnight) {
    }

    Optional<ShiftWindow> shift(String code);

    /** Công suất lý thuyết chuẩn hóa theo phút: {@code unit} = {@code RODS} (điếu) hoặc {@code PACKS} (bao). */
    record MachineRate(BigDecimal perMinute, String unit) {
    }

    /** Chỉ chứa máy đã khai báo công suất lý thuyết (> 0). */
    java.util.Map<UUID, MachineRate> machineRates(java.util.Collection<UUID> ids);

    record StopReasonInfo(String code, String name, String category, boolean countsAsPlannedDowntime,
                          boolean requiresTechConfirm, boolean canCreateRepairRequest) {
    }

    /** Mã dừng máy đang hoạt động (A–I). */
    Optional<StopReasonInfo> stopReason(String code);

    /** Máy được phép chạy sản phẩm; tập rỗng nghĩa là không giới hạn (chưa khai báo). */
    java.util.Set<UUID> allowedMachineIds(UUID productId);
}
