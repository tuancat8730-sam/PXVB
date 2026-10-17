package vn.pxvb.material.application;

import static vn.pxvb.material.application.MtTime.ts;

import com.github.f4b6a3.uuid.UuidCreator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.audit.AuditLogWriter.FieldChange;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.common.sequence.NumberSequenceService;
import vn.pxvb.common.sequence.NumberSequenceService.ResetPolicy;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.masterdata.contract.MasterdataQueries;
import vn.pxvb.masterdata.contract.MasterdataQueries.IssueSlot;
import vn.pxvb.masterdata.contract.MasterdataQueries.MaterialInfo;
import vn.pxvb.masterdata.contract.MasterdataQueries.Norm;
import vn.pxvb.masterdata.contract.MasterdataQueries.NormLine;
import vn.pxvb.material.domain.SlipAllocator;
import vn.pxvb.planning.contract.PlanningEvents.OrderConfirmed;
import vn.pxvb.planning.contract.PlanningQueries;
import vn.pxvb.planning.contract.PlanningQueries.OrderRef;

/**
 * Phiếu cấp phát (FR-MT-001/002): tự sinh khi lệnh SX ca được xác nhận (định mức hiệu lực × sản lượng kế hoạch,
 * tách 3 loại phiếu, chia 4 khung giờ theo {@code SharePct}); STAT_MATERIAL rà soát, chỉnh và xác nhận.
 */
@Service
public class SlipService {

    private static final List<String> SLIP_TYPES = List.of("FIBER", "ROLLING", "PACKING");
    private static final int QTY_SCALE = 3;

    record OrderInfo(UUID orderId, String code, LocalDate date, String shift, UUID makerId, UUID packerId,
                     UUID productId, long plannedPacks, UUID normVersionId, int revision) {
    }

    private final JdbcTemplate jdbc;
    private final MasterdataQueries masterdata;
    private final PlanningQueries planning;
    private final NumberSequenceService numbers;
    private final MtAudit audit;
    private final CurrentUser currentUser;
    private final Clock clock;

    public SlipService(JdbcTemplate jdbc, MasterdataQueries masterdata, PlanningQueries planning,
                       NumberSequenceService numbers, MtAudit audit, CurrentUser currentUser, Clock clock) {
        this.jdbc = jdbc;
        this.masterdata = masterdata;
        this.planning = planning;
        this.numbers = numbers;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // ---------- Sinh phiếu ----------

    /** AC-MT-001: phiếu {@code AUTO} xuất hiện trong cùng giao dịch xác nhận lệnh (≤ 10 giây). */
    @EventListener
    @Transactional
    public void onOrderConfirmed(OrderConfirmed e) {
        generate(new OrderInfo(e.orderId(), e.code(), e.businessDate(), e.shiftCode(), e.makerMachineId(),
                e.packerMachineId(), e.productId(), e.plannedPacks(), e.normVersionId(), e.revision()));
    }

    @Transactional
    int generate(OrderInfo order) {
        Norm norm = masterdata.activeNorm(order.productId(), order.date()).orElse(null);
        if (norm == null || norm.lines().isEmpty()) {
            return 0;
        }
        Map<UUID, MaterialInfo> materials = masterdata.materials(norm.lines().stream()
                .map(NormLine::materialId).toList());
        List<IssueSlot> slots = masterdata.issueSlots(order.shift());
        List<BigDecimal> shares = slots.isEmpty()
                ? List.of(BigDecimal.valueOf(25), BigDecimal.valueOf(25), BigDecimal.valueOf(25),
                BigDecimal.valueOf(25)) : slots.stream().map(IssueSlot::sharePct).toList();
        // nhóm dòng định mức theo (loại phiếu, máy tiêu thụ)
        Map<String, List<NormLine>> groups = new LinkedHashMap<>();
        for (NormLine line : norm.lines()) {
            MaterialInfo m = materials.get(line.materialId());
            String type = m != null && SLIP_TYPES.contains(m.categoryCode()) ? m.categoryCode() : "PACKING";
            UUID machine = consumer(line, type, order);
            groups.computeIfAbsent(type + "|" + machine, k -> new ArrayList<>()).add(line);
        }
        int created = 0;
        for (Map.Entry<String, List<NormLine>> g : groups.entrySet()) {
            String[] key = g.getKey().split("\\|");
            UUID machine = UUID.fromString(key[1]);
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM mt_issue_slip WHERE order_id = "
                    + "UUID_TO_BIN(?) AND slip_type = ? AND consumer_machine_id = UUID_TO_BIN(?) AND revision = ?",
                    Integer.class, order.orderId().toString(), key[0], machine.toString(), order.revision());
            if (exists != null && exists > 0) {
                continue;
            }
            UUID slipId = UuidCreator.getTimeOrderedEpoch();
            String ymd = order.date().toString().replace("-", "");
            String prefix = "PCP-" + ymd + "-" + order.shift() + "-" + key[0];
            String code = numbers.next(prefix.toLowerCase(), prefix, ResetPolicy.NONE);
            jdbc.update("INSERT INTO mt_issue_slip(id, row_version, created_at, updated_at, code, order_id, "
                    + "order_code, business_date, shift_code, slip_type, consumer_machine_id, product_id, "
                    + "norm_version_id, revision, status) VALUES (UUID_TO_BIN(?), 0, ?, ?, ?, UUID_TO_BIN(?), ?, ?, "
                    + "?, ?, UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, 'AUTO')", slipId.toString(),
                    ts(clock.instant()), ts(clock.instant()), code, order.orderId().toString(), order.code(),
                    order.date(), order.shift(), key[0], machine.toString(), order.productId().toString(),
                    norm.normId().toString(), order.revision());
            for (NormLine line : g.getValue()) {
                BigDecimal demand = SlipAllocator.demand(line.qtyPer1000Packs(), order.plannedPacks());
                List<BigDecimal> parts = SlipAllocator.allocate(demand, shares, QTY_SCALE);
                for (int i = 0; i < parts.size(); i++) {
                    int slotNo = slots.isEmpty() ? i + 1 : slots.get(i).slotNo();
                    jdbc.update("INSERT INTO mt_issue_slip_line(id, slip_id, slot_no, material_id, planned_qty, uom) "
                            + "VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, UUID_TO_BIN(?), ?, ?)",
                            UuidCreator.getTimeOrderedEpoch().toString(), slipId.toString(), slotNo,
                            line.materialId().toString(), parts.get(i), line.uom());
                }
            }
            audit.write("IssueSlip", slipId, "AUTO_GENERATE", List.of(new FieldChange("code", null, code),
                    new FieldChange("orderCode", null, order.code())), null);
            created++;
        }
        return created;
    }

    /** BR-MT-002: NVL sợi/vấn → máy vấn; bao → máy bao; {@code consumerType} trên dòng định mức ghi đè. */
    private static UUID consumer(NormLine line, String type, OrderInfo order) {
        String override = line.consumerType();
        if ("MAKER".equalsIgnoreCase(override)) {
            return order.makerId();
        }
        if ("PACKER".equalsIgnoreCase(override)) {
            return order.packerId();
        }
        return "PACKING".equals(type) ? order.packerId() : order.makerId();
    }

    /** Sinh lại phiếu {@code AUTO} (vd sau khi định mức/sản lượng kế hoạch đổi); không đụng phiếu đã xác nhận. */
    @Transactional
    public int regenerate(UUID orderId) {
        List<Map<String, Object>> slips = jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, status, business_date, "
                + "shift_code, revision FROM mt_issue_slip WHERE order_id = UUID_TO_BIN(?)", orderId.toString());
        if (slips.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Lệnh chưa có phiếu cấp phát");
        }
        if (slips.stream().anyMatch(s -> !"AUTO".equals(s.get("status")))) {
            throw new BusinessException(ErrorCode.DATA_LOCKED,
                    "Đã có phiếu được xác nhận; chỉ chỉnh được bằng phiếu bản sửa đổi/yêu cầu điều chỉnh");
        }
        LocalDate date = ((java.sql.Date) slips.get(0).get("business_date")).toLocalDate();
        String shift = (String) slips.get(0).get("shift_code");
        int revision = ((Number) slips.get(0).get("revision")).intValue();
        OrderRef order = planning.activeOrders(date, shift).stream().filter(o -> o.orderId().equals(orderId))
                .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                        "Lệnh không còn ở trạng thái xác nhận/đang chạy"));
        jdbc.update("DELETE FROM mt_issue_slip WHERE order_id = UUID_TO_BIN(?) AND status = 'AUTO'",
                orderId.toString());
        return generate(new OrderInfo(orderId, null, date, shift, order.makerMachineId(), order.packerMachineId(),
                order.productId(), order.plannedPacks(), null, revision));
    }

    // ---------- Rà soát ----------

    @Transactional
    public Map<String, Object> updateLine(UUID slipId, UUID lineId, BigDecimal qty, String reason) {
        Map<String, Object> slip = require(slipId);
        if (!"AUTO".equals(slip.get("status"))) {
            throw new BusinessException(ErrorCode.DATA_LOCKED,
                    "Phiếu đã xác nhận, không sửa trực tiếp (BR-MT-009)");
        }
        if (qty == null || qty.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION, "Số lượng phải ≥ 0");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cần nhập lý do khi chỉnh số lượng",
                    Map.of("reason", List.of("Vui lòng nhập lý do")));
        }
        List<BigDecimal> before = jdbc.queryForList("SELECT planned_qty FROM mt_issue_slip_line WHERE id = "
                + "UUID_TO_BIN(?) AND slip_id = UUID_TO_BIN(?)", BigDecimal.class, lineId.toString(),
                slipId.toString());
        if (before.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy dòng phiếu");
        }
        jdbc.update("UPDATE mt_issue_slip_line SET planned_qty = ?, adjust_reason = ? WHERE id = UUID_TO_BIN(?)",
                qty.setScale(QTY_SCALE, java.math.RoundingMode.HALF_UP), reason.trim(), lineId.toString());
        audit.write("IssueSlip", slipId, "UPDATE_LINE", List.of(new FieldChange("plannedQty",
                before.get(0).toPlainString(), qty.toPlainString())), reason);
        return get(slipId);
    }

    @Transactional
    public Map<String, Object> confirm(UUID slipId) {
        Map<String, Object> slip = require(slipId);
        if (!"AUTO".equals(slip.get("status"))) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Chỉ xác nhận được phiếu ở trạng thái AUTO (hiện tại " + slip.get("status") + ")");
        }
        UUID userId = currentUser.require().userId();
        jdbc.update("UPDATE mt_issue_slip SET status = 'CONFIRMED', confirmed_by = UUID_TO_BIN(?), confirmed_at = ?, "
                + "row_version = row_version + 1, updated_at = ?, updated_by = UUID_TO_BIN(?) WHERE id = "
                + "UUID_TO_BIN(?)", userId.toString(), ts(clock.instant()), ts(clock.instant()), userId.toString(),
                slipId.toString());
        audit.write("IssueSlip", slipId, "CONFIRM", List.of(new FieldChange("status", "AUTO", "CONFIRMED")), null);
        return get(slipId);
    }

    @Transactional
    public List<Map<String, Object>> confirmAll(List<UUID> ids) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (UUID id : ids) {
            out.add(confirm(id));
        }
        return out;
    }

    // ---------- Truy vấn ----------

    private Map<String, Object> require(UUID slipId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, status, business_date, "
                + "shift_code FROM mt_issue_slip WHERE id = UUID_TO_BIN(?) FOR UPDATE", slipId.toString());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiếu cấp phát");
        }
        return rows.get(0);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(LocalDate date, String shift, String type, UUID machineId, String status) {
        StringBuilder sql = new StringBuilder(headerSql()).append(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (date != null) {
            sql.append(" AND s.business_date = ?");
            args.add(date);
        }
        if (shift != null && !shift.isBlank()) {
            sql.append(" AND s.shift_code = ?");
            args.add(shift.toUpperCase());
        }
        if (type != null && !type.isBlank()) {
            sql.append(" AND s.slip_type = ?");
            args.add(type.toUpperCase());
        }
        if (machineId != null) {
            sql.append(" AND s.consumer_machine_id = UUID_TO_BIN(?)");
            args.add(machineId.toString());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND s.status = ?");
            args.add(status.toUpperCase());
        }
        sql.append(" ORDER BY s.business_date, s.shift_code, s.slip_type, s.code");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID slipId) {
        List<Map<String, Object>> rows = jdbc.queryForList(headerSql() + " WHERE s.id = UUID_TO_BIN(?)",
                slipId.toString());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiếu cấp phát");
        }
        Map<String, Object> out = new LinkedHashMap<>(rows.get(0));
        out.put("lines", jdbc.queryForList("SELECT BIN_TO_UUID(l.id) AS id, l.slot_no AS slotNo, "
                + "BIN_TO_UUID(l.material_id) AS materialId, l.planned_qty AS plannedQty, l.uom, "
                + "l.adjust_reason AS adjustReason, COALESCE((SELECT SUM(r.issued_qty) FROM mt_issue_record r "
                + "WHERE r.slip_line_id = l.id), 0) AS issuedQty FROM mt_issue_slip_line l WHERE l.slip_id = "
                + "UUID_TO_BIN(?) ORDER BY l.slot_no, l.material_id", slipId.toString()));
        return out;
    }

    /** Danh sách soạn hàng theo khung giờ cho tablet kho (FR-MT-003). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> worklist(LocalDate date, String shift, Integer slotNo) {
        StringBuilder sql = new StringBuilder("SELECT BIN_TO_UUID(l.id) AS lineId, BIN_TO_UUID(s.id) AS slipId, "
                + "s.code AS slipCode, s.slip_type AS slipType, BIN_TO_UUID(s.consumer_machine_id) AS machineId, "
                + "l.slot_no AS slotNo, BIN_TO_UUID(l.material_id) AS materialId, l.planned_qty AS plannedQty, "
                + "l.uom, COALESCE((SELECT SUM(r.issued_qty) FROM mt_issue_record r WHERE r.slip_line_id = l.id), "
                + "0) AS issuedQty FROM mt_issue_slip_line l JOIN mt_issue_slip s ON s.id = l.slip_id WHERE "
                + "s.business_date = ? AND s.shift_code = ? AND s.status IN ('CONFIRMED','ISSUING')");
        List<Object> args = new ArrayList<>(List.of(date, shift.toUpperCase()));
        if (slotNo != null) {
            sql.append(" AND l.slot_no = ?");
            args.add(slotNo);
        }
        sql.append(" ORDER BY s.consumer_machine_id, l.slot_no, l.material_id");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    private static String headerSql() {
        return "SELECT BIN_TO_UUID(s.id) AS id, s.code, BIN_TO_UUID(s.order_id) AS orderId, s.order_code AS "
                + "orderCode, s.business_date AS businessDate, s.shift_code AS shiftCode, s.slip_type AS slipType, "
                + "BIN_TO_UUID(s.consumer_machine_id) AS consumerMachineId, BIN_TO_UUID(s.product_id) AS productId, "
                + "BIN_TO_UUID(s.norm_version_id) AS normVersionId, s.revision, s.status, BIN_TO_UUID("
                + "s.confirmed_by) AS confirmedBy, s.confirmed_at AS confirmedAt FROM mt_issue_slip s";
    }
}
