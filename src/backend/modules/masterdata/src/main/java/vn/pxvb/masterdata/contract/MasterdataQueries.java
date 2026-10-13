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
}
