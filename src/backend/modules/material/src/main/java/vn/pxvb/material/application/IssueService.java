package vn.pxvb.material.application;

import static vn.pxvb.material.application.MtTime.ts;

import com.github.f4b6a3.uuid.UuidCreator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.alerting.contract.AlertRaiser;
import vn.pxvb.alerting.contract.AlertRaiser.AlertCommand;
import vn.pxvb.common.audit.AuditLogWriter.FieldChange;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.masterdata.contract.MasterdataQueries;
import vn.pxvb.masterdata.contract.MasterdataQueries.MaterialInfo;

/**
 * Cấp phát thực tế, xác nhận nhận, hoàn trả, kiểm đếm (FR-MT-003/004/005). Dữ liệu từ tablet giữ
 * {@code OccurredAt}; lệnh gửi lại cùng {@code id} trả bản ghi cũ.
 */
@Service
public class IssueService {

    public record Actor(UUID userId, UUID deviceId) {
    }

    public record IssueInput(UUID id, UUID slipLineId, LocalDate businessDate, String shiftCode, Integer slotNo,
                             UUID machineId, UUID materialId, String lotNo, BigDecimal issuedQty,
                             String reasonCode, String note, Instant occurredAt) {
    }

    public record ReceiptInput(UUID id, UUID issueRecordId, BigDecimal receivedQty, BigDecimal remainingBefore,
                               String note, Instant occurredAt) {
    }

    public record ReturnInput(UUID id, LocalDate businessDate, String shiftCode, UUID machineId, UUID materialId,
                              BigDecimal qty, String reason, Instant occurredAt) {
    }

    public record CountInput(UUID id, LocalDate businessDate, String shiftCode, UUID machineId, UUID materialId,
                             String kind, BigDecimal qty, String note, Instant occurredAt) {
    }

    private static final java.util.Set<String> KINDS = java.util.Set.of("OPENING", "MIDSHIFT", "CLOSING");
    private static final BigDecimal DEFAULT_OVER_PCT = BigDecimal.TEN;

    private final JdbcTemplate jdbc;
    private final MasterdataQueries masterdata;
    private final ConsumptionService consumption;
    private final AlertRaiser alerts;
    private final MtAudit audit;
    private final CurrentUser currentUser;
    private final Clock clock;

    public IssueService(JdbcTemplate jdbc, MasterdataQueries masterdata, ConsumptionService consumption,
                        AlertRaiser alerts, MtAudit audit, CurrentUser currentUser, Clock clock) {
        this.jdbc = jdbc;
        this.masterdata = masterdata;
        this.consumption = consumption;
        this.alerts = alerts;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // ---------- Cấp phát ----------

    @Transactional
    public Map<String, Object> recordIssue(IssueInput in, Actor actor) {
        if (in.id() != null) {
            List<Map<String, Object>> dup = jdbc.queryForList(issueSql() + " WHERE r.id = UUID_TO_BIN(?)",
                    in.id().toString());
            if (!dup.isEmpty()) {
                return dup.get(0);
            }
        }
        if (in.issuedQty() == null || in.issuedQty().signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION, "Số lượng cấp phải ≥ 0");
        }
        LocalDate date = in.businessDate();
        String shift = in.shiftCode() == null ? null : in.shiftCode().toUpperCase();
        Integer slot = in.slotNo();
        UUID machineId = in.machineId();
        UUID materialId = in.materialId();
        UUID slipId = null;
        BigDecimal planned = null;
        BigDecimal alreadyIssued = BigDecimal.ZERO;
        String uom;
        if (in.slipLineId() != null) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT BIN_TO_UUID(s.id) AS slipId, s.status, "
                    + "s.business_date, s.shift_code, BIN_TO_UUID(s.consumer_machine_id) AS machineId, l.slot_no, "
                    + "BIN_TO_UUID(l.material_id) AS materialId, l.planned_qty, l.uom, COALESCE((SELECT SUM("
                    + "r.issued_qty) FROM mt_issue_record r WHERE r.slip_line_id = l.id), 0) AS issued FROM "
                    + "mt_issue_slip_line l JOIN mt_issue_slip s ON s.id = l.slip_id WHERE l.id = UUID_TO_BIN(?) "
                    + "FOR UPDATE", in.slipLineId().toString());
            if (rows.isEmpty()) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy dòng phiếu cấp phát");
            }
            Map<String, Object> r = rows.get(0);
            if ("AUTO".equals(r.get("status")) || "CANCELLED".equals(r.get("status"))) {
                throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                        "Phiếu chưa được STAT_MATERIAL xác nhận (hoặc đã hủy), chưa thể cấp phát");
            }
            slipId = UUID.fromString((String) r.get("slipId"));
            date = ((java.sql.Date) r.get("business_date")).toLocalDate();
            shift = (String) r.get("shift_code");
            slot = ((Number) r.get("slot_no")).intValue();
            machineId = UUID.fromString((String) r.get("machineId"));
            materialId = UUID.fromString((String) r.get("materialId"));
            planned = (BigDecimal) r.get("planned_qty");
            alreadyIssued = (BigDecimal) r.get("issued");
            uom = (String) r.get("uom");
        } else {
            if (date == null || shift == null || slot == null || machineId == null || materialId == null) {
                throw new BusinessException(ErrorCode.VALIDATION,
                        "Cấp ngoài phiếu cần ngày, ca, khung giờ, máy và NVL");
            }
            if (in.reasonCode() == null || in.reasonCode().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION, "Cấp bổ sung ngoài phiếu phải có lý do",
                        Map.of("reasonCode", List.of("Vui lòng chọn lý do")));
            }
            MaterialInfo m = masterdata.materials(List.of(materialId)).get(materialId);
            if (m == null) {
                throw new BusinessException(ErrorCode.VALIDATION, "NVL không tồn tại");
            }
            uom = m.uom();
        }
        BigDecimal variance = null;
        if (planned != null) {
            BigDecimal cumulative = alreadyIssued.add(in.issuedQty());
            variance = cumulative.subtract(planned);
            BigDecimal pct = planned.signum() == 0 ? (cumulative.signum() == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(100)) : variance.abs().multiply(BigDecimal.valueOf(100))
                    .divide(planned, 3, RoundingMode.HALF_UP);
            BigDecimal limit = masterdata.resolveThreshold("MT_ISSUE_OVER_SLIP_PCT", machineId, null, null, null, null,
                    date).map(t -> t.value()).orElse(DEFAULT_OVER_PCT);
            if (pct.compareTo(limit) > 0) {
                requireVarianceReason(in.reasonCode());
                if (variance.signum() > 0) {
                    alerts.raise(new AlertCommand("MT_ISSUE_OVER_SLIP", "IssueSlipLine",
                            in.slipLineId().toString(), machineId, null, "Cấp vượt phiếu",
                            "Cấp lũy kế " + cumulative.stripTrailingZeros().toPlainString() + " " + uom
                            + " so với phiếu " + planned.stripTrailingZeros().toPlainString() + " (lệch " + pct
                            + "%)", Map.of("slipLineId", in.slipLineId().toString(), "variancePct", pct), null, null));
                }
            }
        }
        Instant now = clock.instant();
        UUID id = in.id() == null ? UuidCreator.getTimeOrderedEpoch() : in.id();
        jdbc.update("INSERT INTO mt_issue_record(id, row_version, created_at, created_by, slip_id, slip_line_id, "
                        + "business_date, shift_code, slot_no, consumer_machine_id, material_id, lot_no, issued_qty, "
                        + "uom, issued_by, issued_at, variance_qty, variance_reason_code, variance_note, device_id) "
                        + "VALUES (UUID_TO_BIN(?), 0, ?, UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, "
                        + "UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, UUID_TO_BIN(?), ?, ?, ?, ?, UUID_TO_BIN(?))",
                id.toString(), ts(now), str(actor.userId()), str(slipId), str(in.slipLineId()), date, shift, slot,
                machineId.toString(), materialId.toString(), in.lotNo(), in.issuedQty(), uom, str(actor.userId()),
                ts(in.occurredAt() == null ? now : in.occurredAt()), variance, in.reasonCode(), in.note(),
                str(actor.deviceId()));
        if (slipId != null) {
            jdbc.update("UPDATE mt_issue_slip SET status = 'ISSUING', row_version = row_version + 1 WHERE id = "
                    + "UUID_TO_BIN(?) AND status = 'CONFIRMED'", slipId.toString());
        }
        audit.write("IssueRecord", id, "ISSUE", List.of(new FieldChange("issuedQty", null,
                in.issuedQty().toPlainString()), new FieldChange("materialId", null, materialId.toString())),
                in.note(), actor.userId(), actor.deviceId());
        consumption.recompute(date, shift);
        return jdbc.queryForList(issueSql() + " WHERE r.id = UUID_TO_BIN(?)", id.toString()).get(0);
    }

    private void requireVarianceReason(String reasonCode) {
        if (reasonCode == null || !masterdata.reasonExists("MATERIAL_VARIANCE", reasonCode)) {
            throw new BusinessException(ErrorCode.VALIDATION,
                    "Chênh lệch so với phiếu vượt ngưỡng: cần chọn lý do từ danh mục",
                    Map.of("reasonCode", List.of("Lý do không hợp lệ")));
        }
    }

    // ---------- Nhận ----------

    @Transactional
    public Map<String, Object> confirmReceipt(ReceiptInput in, Actor actor) {
        if (in.id() != null) {
            List<Map<String, Object>> dup = jdbc.queryForList(receiptSql() + " WHERE c.id = UUID_TO_BIN(?)",
                    in.id().toString());
            if (!dup.isEmpty()) {
                return dup.get(0);
            }
        }
        if (in.receivedQty() == null || in.receivedQty().signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION, "Số lượng nhận phải ≥ 0");
        }
        List<Map<String, Object>> issue = jdbc.queryForList("SELECT issued_qty, business_date, shift_code FROM "
                + "mt_issue_record WHERE id = UUID_TO_BIN(?) FOR UPDATE", in.issueRecordId().toString());
        if (issue.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy bản ghi cấp phát");
        }
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM mt_receipt_confirmation WHERE issue_record_id = "
                + "UUID_TO_BIN(?)", Integer.class, in.issueRecordId().toString());
        if (existing != null && existing > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "Bản ghi cấp phát đã được xác nhận nhận");
        }
        BigDecimal issued = (BigDecimal) issue.get(0).get("issued_qty");
        boolean match = in.receivedQty().compareTo(issued) == 0;
        if (!match && (in.note() == null || in.note().isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION, "Số nhận khác số giao: cần ghi chú lý do",
                    Map.of("note", List.of("Vui lòng nhập ghi chú")));
        }
        Instant now = clock.instant();
        UUID id = in.id() == null ? UuidCreator.getTimeOrderedEpoch() : in.id();
        jdbc.update("INSERT INTO mt_receipt_confirmation(id, row_version, created_at, issue_record_id, received_qty, "
                        + "received_by, received_at, remaining_before_receipt, status, dispute_note, device_id) "
                        + "VALUES (UUID_TO_BIN(?), 0, ?, UUID_TO_BIN(?), ?, UUID_TO_BIN(?), ?, ?, ?, ?, UUID_TO_BIN(?))",
                id.toString(), ts(now), in.issueRecordId().toString(), in.receivedQty(), str(actor.userId()),
                ts(in.occurredAt() == null ? now : in.occurredAt()), in.remainingBefore(),
                match ? "CONFIRMED" : "DISPUTED", match ? null : in.note(), str(actor.deviceId()));
        audit.write("ReceiptConfirmation", id, match ? "CONFIRM" : "DISPUTE", List.of(
                new FieldChange("receivedQty", issued.toPlainString(), in.receivedQty().toPlainString())),
                in.note(), actor.userId(), actor.deviceId());
        return jdbc.queryForList(receiptSql() + " WHERE c.id = UUID_TO_BIN(?)", id.toString()).get(0);
    }

    /** BR-MT-007: STAT_MATERIAL xử lý tranh chấp trước khi chốt ca. */
    @Transactional
    public Map<String, Object> resolveReceipt(UUID receiptId, String note) {
        if (note == null || note.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cần nhập nội dung xử lý tranh chấp",
                    Map.of("note", List.of("Vui lòng nhập nội dung xử lý")));
        }
        UUID userId = currentUser.require().userId();
        int updated = jdbc.update("UPDATE mt_receipt_confirmation SET status = 'RESOLVED', resolved_by = "
                + "UUID_TO_BIN(?), resolved_at = ?, resolution_note = ?, row_version = row_version + 1 WHERE id = "
                + "UUID_TO_BIN(?) AND status = 'DISPUTED'", userId.toString(), ts(clock.instant()), note.trim(),
                receiptId.toString());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Chỉ xử lý được xác nhận nhận đang ở trạng thái DISPUTED");
        }
        audit.write("ReceiptConfirmation", receiptId, "RESOLVE", List.of(new FieldChange("status", "DISPUTED",
                "RESOLVED")), note);
        return jdbc.queryForList(receiptSql() + " WHERE c.id = UUID_TO_BIN(?)", receiptId.toString()).get(0);
    }

    // ---------- Hoàn trả & kiểm đếm ----------

    @Transactional
    public Map<String, Object> recordReturn(ReturnInput in, Actor actor) {
        if (in.id() != null) {
            List<Map<String, Object>> dup = jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, qty FROM "
                    + "mt_return_record WHERE id = UUID_TO_BIN(?)", in.id().toString());
            if (!dup.isEmpty()) {
                return dup.get(0);
            }
        }
        if (in.qty() == null || in.qty().signum() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION, "Số lượng hoàn trả phải > 0");
        }
        requireScope(in.businessDate(), in.shiftCode(), in.machineId(), in.materialId());
        Instant now = clock.instant();
        UUID id = in.id() == null ? UuidCreator.getTimeOrderedEpoch() : in.id();
        String shift = in.shiftCode().toUpperCase();
        jdbc.update("INSERT INTO mt_return_record(id, created_at, created_by, business_date, shift_code, "
                        + "consumer_machine_id, material_id, qty, reason, returned_by, returned_at, device_id) VALUES ("
                        + "UUID_TO_BIN(?), ?, UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, "
                        + "UUID_TO_BIN(?), ?, UUID_TO_BIN(?))", id.toString(), ts(now), str(actor.userId()),
                in.businessDate(), shift, in.machineId().toString(), in.materialId().toString(), in.qty(),
                in.reason(), str(actor.userId()), ts(in.occurredAt() == null ? now : in.occurredAt()),
                str(actor.deviceId()));
        audit.write("ReturnRecord", id, "RETURN", List.of(new FieldChange("qty", null, in.qty().toPlainString())),
                in.reason(), actor.userId(), actor.deviceId());
        consumption.recompute(in.businessDate(), shift);
        return Map.of("id", id.toString(), "qty", in.qty());
    }

    @Transactional
    public Map<String, Object> recordCount(CountInput in, Actor actor) {
        if (in.id() != null) {
            List<Map<String, Object>> dup = jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, kind, counted_qty AS "
                    + "countedQty FROM mt_stock_count WHERE id = UUID_TO_BIN(?)", in.id().toString());
            if (!dup.isEmpty()) {
                return dup.get(0);
            }
        }
        String kind = in.kind() == null ? "" : in.kind().toUpperCase();
        if (!KINDS.contains(kind)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Loại kiểm đếm phải là OPENING, MIDSHIFT hoặc CLOSING");
        }
        if (in.qty() == null || in.qty().signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION, "Số lượng tồn phải ≥ 0");
        }
        requireScope(in.businessDate(), in.shiftCode(), in.machineId(), in.materialId());
        String shift = in.shiftCode().toUpperCase();
        if ("OPENING".equals(kind)) {
            // AC-MT-005: tồn đầu mặc định = tồn cuối ca trước; sửa được nhưng phải kèm lý do (audit)
            BigDecimal auto = consumption.autoOpening(in.machineId(), in.materialId(), in.businessDate(), shift);
            if (auto.compareTo(in.qty()) != 0 && (in.note() == null || in.note().isBlank())) {
                throw new BusinessException(ErrorCode.VALIDATION, "Tồn đầu khác tồn cuối ca trước ("
                        + auto.stripTrailingZeros().toPlainString() + "): cần ghi lý do",
                        Map.of("note", List.of("Vui lòng nhập lý do")));
            }
        }
        Instant now = clock.instant();
        UUID id = in.id() == null ? UuidCreator.getTimeOrderedEpoch() : in.id();
        jdbc.update("INSERT INTO mt_stock_count(id, created_at, created_by, business_date, shift_code, "
                        + "consumer_machine_id, material_id, kind, counted_qty, counted_by, counted_at, note, "
                        + "device_id) VALUES (UUID_TO_BIN(?), ?, UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), "
                        + "UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?))", id.toString(), ts(now),
                str(actor.userId()), in.businessDate(), shift, in.machineId().toString(), in.materialId().toString(),
                kind, in.qty(), str(actor.userId()), ts(in.occurredAt() == null ? now : in.occurredAt()), in.note(),
                str(actor.deviceId()));
        audit.write("StockCount", id, "COUNT_" + kind, List.of(new FieldChange("countedQty", null,
                in.qty().toPlainString())), in.note(), actor.userId(), actor.deviceId());
        consumption.recompute(in.businessDate(), shift);
        return Map.of("id", id.toString(), "kind", kind, "countedQty", in.qty());
    }

    // ---------- Truy vấn ----------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> receipts(LocalDate date, String shift, String status) {
        StringBuilder sql = new StringBuilder(receiptSql()).append(" JOIN mt_issue_record r ON r.id = "
                + "c.issue_record_id WHERE r.business_date = ?");
        List<Object> args = new java.util.ArrayList<>(List.of(date));
        if (shift != null && !shift.isBlank()) {
            sql.append(" AND r.shift_code = ?");
            args.add(shift.toUpperCase());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND c.status = ?");
            args.add(status.toUpperCase());
        }
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> issues(LocalDate date, String shift, UUID machineId) {
        StringBuilder sql = new StringBuilder(issueSql()).append(" WHERE r.business_date = ?");
        List<Object> args = new java.util.ArrayList<>(List.of(date));
        if (shift != null && !shift.isBlank()) {
            sql.append(" AND r.shift_code = ?");
            args.add(shift.toUpperCase());
        }
        if (machineId != null) {
            sql.append(" AND r.consumer_machine_id = UUID_TO_BIN(?)");
            args.add(machineId.toString());
        }
        sql.append(" ORDER BY r.issued_at");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    private void requireScope(LocalDate date, String shift, UUID machineId, UUID materialId) {
        if (date == null || shift == null || shift.isBlank() || machineId == null || materialId == null) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cần ngày, ca, máy và NVL");
        }
    }

    private static String str(UUID id) {
        return id == null ? null : id.toString();
    }

    private static String issueSql() {
        return "SELECT BIN_TO_UUID(r.id) AS id, BIN_TO_UUID(r.slip_id) AS slipId, BIN_TO_UUID(r.slip_line_id) AS "
                + "slipLineId, r.business_date AS businessDate, r.shift_code AS shiftCode, r.slot_no AS slotNo, "
                + "BIN_TO_UUID(r.consumer_machine_id) AS machineId, BIN_TO_UUID(r.material_id) AS materialId, "
                + "r.lot_no AS lotNo, r.issued_qty AS issuedQty, r.uom, r.variance_qty AS varianceQty, "
                + "r.variance_reason_code AS varianceReasonCode, r.variance_note AS varianceNote, r.issued_at AS "
                + "issuedAt FROM mt_issue_record r";
    }

    private static String receiptSql() {
        return "SELECT BIN_TO_UUID(c.id) AS id, BIN_TO_UUID(c.issue_record_id) AS issueRecordId, c.received_qty AS "
                + "receivedQty, c.remaining_before_receipt AS remainingBeforeReceipt, c.status, c.dispute_note AS "
                + "disputeNote, c.received_at AS receivedAt, c.resolution_note AS resolutionNote FROM "
                + "mt_receipt_confirmation c";
    }
}
