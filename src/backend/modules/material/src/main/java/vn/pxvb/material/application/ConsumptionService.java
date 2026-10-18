package vn.pxvb.material.application;

import static vn.pxvb.material.application.MtTime.BUSINESS_ZONE;
import static vn.pxvb.material.application.MtTime.ts;

import com.github.f4b6a3.uuid.UuidCreator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.alerting.contract.AlertRaiser;
import vn.pxvb.alerting.contract.AlertRaiser.AlertCommand;
import vn.pxvb.common.audit.AuditLogWriter.FieldChange;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.common.outbox.OutboxPublisher;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.masterdata.contract.MasterdataQueries;
import vn.pxvb.masterdata.contract.MasterdataQueries.IssueSlot;
import vn.pxvb.masterdata.contract.MasterdataQueries.MaterialInfo;
import vn.pxvb.masterdata.contract.MasterdataQueries.Norm;
import vn.pxvb.masterdata.contract.MasterdataQueries.NormLine;
import vn.pxvb.masterdata.contract.MasterdataQueries.PairingLine;
import vn.pxvb.material.domain.ConsumptionCalculator;
import vn.pxvb.material.domain.ConsumptionCalculator.Flag;
import vn.pxvb.material.domain.ConsumptionCalculator.Input;
import vn.pxvb.material.domain.ConsumptionCalculator.Result;
import vn.pxvb.production.contract.ProductionEvents.ShiftEnded;
import vn.pxvb.production.contract.ProductionQueries;
import vn.pxvb.production.contract.ProductionQueries.SessionRef;

/**
 * Tiêu hao NVL theo ca – máy – NVL (FR-MT-006..009): so thực tế với định mức hiệu lực × sản lượng đạt (theo mác),
 * tính tồn đầu/cuối liên tục (BR-MT-008), cảnh báo {@code MT_WASTE_HIGH}, chốt tiêu hao ca.
 */
@Service
public class ConsumptionService {

    private static final BigDecimal DEFAULT_WASTE_PCT = BigDecimal.valueOf(3);
    private static final int DEFAULT_TOLERANCE_MIN = 30;

    private record Key(UUID machineId, UUID materialId) {
    }

    private final JdbcTemplate jdbc;
    private final MasterdataQueries masterdata;
    private final ProductionQueries production;
    private final AlertRaiser alerts;
    private final MtAudit audit;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final OutboxPublisher outbox;

    public ConsumptionService(JdbcTemplate jdbc, MasterdataQueries masterdata, ProductionQueries production,
                              AlertRaiser alerts, MtAudit audit, CurrentUser currentUser, Clock clock,
                              OutboxPublisher outbox) {
        this.jdbc = jdbc;
        this.masterdata = masterdata;
        this.production = production;
        this.alerts = alerts;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
        this.outbox = outbox;
    }

    @EventListener
    @Transactional
    public void onShiftEnded(ShiftEnded event) {
        compute(event.sessionId());
    }

    /** Tính lại khi có dữ liệu mới của ca (nếu phiên ca tồn tại và chưa chốt). */
    @Transactional
    public void recompute(LocalDate date, String shift) {
        production.session(date, shift).filter(s -> !Set.of("CLOSED", "FINALIZED").contains(s.status()))
                .ifPresent(s -> compute(s.id()));
    }

    // ---------- Tồn đầu ----------

    /** BR-MT-008: tồn cuối ca trước (tổng hợp, hoặc kiểm đếm CLOSING gần nhất) của (máy, NVL); không có → 0. */
    @Transactional(readOnly = true)
    public BigDecimal autoOpening(UUID machineId, UUID materialId, LocalDate date, String shift) {
        List<BigDecimal> prev = jdbc.queryForList("SELECT closing_qty FROM mt_consumption_summary WHERE "
                + "consumer_machine_id = UUID_TO_BIN(?) AND material_id = UUID_TO_BIN(?) AND (business_date < ? OR "
                + "(business_date = ? AND shift_code < ?)) ORDER BY business_date DESC, shift_code DESC LIMIT 1",
                BigDecimal.class, machineId.toString(), materialId.toString(), date, date, shift);
        if (!prev.isEmpty()) {
            return prev.get(0);
        }
        List<BigDecimal> counted = jdbc.queryForList("SELECT counted_qty FROM mt_stock_count WHERE "
                + "consumer_machine_id = UUID_TO_BIN(?) AND material_id = UUID_TO_BIN(?) AND kind = 'CLOSING' AND "
                + "(business_date < ? OR (business_date = ? AND shift_code < ?)) ORDER BY business_date DESC, "
                + "shift_code DESC, counted_at DESC LIMIT 1", BigDecimal.class, machineId.toString(),
                materialId.toString(), date, date, shift);
        return counted.isEmpty() ? BigDecimal.ZERO : counted.get(0);
    }

    private BigDecimal openingFor(Key key, LocalDate date, String shift) {
        List<BigDecimal> manual = jdbc.queryForList("SELECT counted_qty FROM mt_stock_count WHERE "
                + "consumer_machine_id = UUID_TO_BIN(?) AND material_id = UUID_TO_BIN(?) AND business_date = ? AND "
                + "shift_code = ? AND kind = 'OPENING' ORDER BY counted_at DESC LIMIT 1", BigDecimal.class,
                key.machineId().toString(), key.materialId().toString(), date, shift);
        return manual.isEmpty() ? autoOpening(key.machineId(), key.materialId(), date, shift) : manual.get(0);
    }

    // ---------- Tính ----------

    @Transactional
    public List<Map<String, Object>> compute(UUID sessionId) {
        SessionRef session = production.session(sessionId).orElseThrow(() -> new BusinessException(
                ErrorCode.NOT_FOUND, "Không tìm thấy phiên ca"));
        LocalDate date = session.businessDate();
        String shift = session.shiftCode();
        boolean ended = !"OPEN".equals(session.status());
        Map<Key, BigDecimal> theoretical = theoretical(sessionId, date, shift);

        Set<Key> keys = new HashSet<>(theoretical.keySet());
        for (String table : List.of("mt_issue_record", "mt_return_record", "mt_stock_count")) {
            jdbc.query("SELECT DISTINCT BIN_TO_UUID(consumer_machine_id) AS m, BIN_TO_UUID(material_id) AS mat FROM "
                    + table + " WHERE business_date = ? AND shift_code = ?", rs -> {
                keys.add(new Key(UUID.fromString(rs.getString("m")), UUID.fromString(rs.getString("mat"))));
            }, date, shift);
        }
        Map<UUID, MaterialInfo> materials = masterdata.materials(keys.stream().map(Key::materialId).distinct()
                .toList());
        Instant now = clock.instant();
        Map<String, String> before = summarySignatures(sessionId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Key key : keys) {
            BigDecimal opening = openingFor(key, date, shift);
            BigDecimal issued = sum("mt_issue_record", "issued_qty", key, date, shift);
            BigDecimal returned = sum("mt_return_record", "qty", key, date, shift);
            BigDecimal theo = theoretical.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal counted = latestCount(key, date, shift);
            if (opening.signum() == 0 && issued.signum() == 0 && returned.signum() == 0 && theo.signum() == 0
                    && counted == null) {
                continue;
            }
            MaterialInfo material = materials.get(key.materialId());
            String group = material == null ? null : material.groupCode() != null ? material.groupCode()
                    : material.categoryCode();
            BigDecimal threshold = masterdata.resolveThreshold("MT_WASTE_PCT", key.machineId(), null, group, null,
                    null, date).map(t -> t.value()).orElse(DEFAULT_WASTE_PCT);
            Result r = ConsumptionCalculator.compute(new Input(opening, issued, returned, counted, theo, threshold,
                    ended));
            out.add(upsert(sessionId, date, shift, key, opening, issued, returned, theo, threshold, r, now));
            if (r.flag() != Flag.OK) {
                alerts.raise(new AlertCommand("MT_WASTE_HIGH", "MachineMaterial", key.machineId() + ":"
                        + key.materialId(), key.machineId(), sessionId, "Hao hụt NVL bất thường",
                        (material == null ? key.materialId().toString() : material.code()) + ": hao hụt "
                        + (r.wastePct() == null ? "ngoài định mức" : r.wastePct().stripTrailingZeros()
                        .toPlainString() + "% (ngưỡng " + threshold.stripTrailingZeros().toPlainString() + "%)"),
                        Map.of("wasteQty", r.wasteQty(), "theoretical", theo), null,
                        r.flag() == Flag.CRIT ? "CRITICAL" : null));
            }
        }
        // Chỉ phát khi nội dung đổi (không tính computed_at): scanner tính lại định kỳ, tránh dồn sự kiện cho RP
        if (!before.equals(summarySignatures(sessionId))) {
            publishChanged(session);
        }
        return out;
    }

    /** Chữ ký nội dung các dòng tiêu hao của phiên ca (bỏ {@code computed_at}) để biết lần tính có làm đổi số liệu. */
    private Map<String, String> summarySignatures(UUID sessionId) {
        Map<String, String> out = new HashMap<>();
        jdbc.query("SELECT BIN_TO_UUID(id) AS id, CONCAT_WS('|', opening_qty, issued_qty, returned_qty, closing_qty, "
                + "counted, actual_consumed, theoretical_consumed, waste_qty, IFNULL(waste_pct, '~'), threshold_pct, "
                + "flag, status, IFNULL(explanation, '~')) AS sig FROM mt_consumption_summary WHERE session_id = "
                + "UUID_TO_BIN(?)", rs -> {
            out.put(rs.getString("id"), rs.getString("sig"));
        }, sessionId.toString());
        return out;
    }

    private void publishChanged(SessionRef session) {
        outbox.publish("mt.consumption.computed", Map.of("sessionId", session.id().toString(), "businessDate",
                session.businessDate().toString(), "shiftCode", session.shiftCode()));
    }

    /** Định mức hiệu lực × sản lượng đạt (bao) theo mác; máy vấn nhận phần sản lượng của máy bao × FeedRatio. */
    private Map<Key, BigDecimal> theoretical(UUID sessionId, LocalDate date, String shift) {
        Map<Key, BigDecimal> out = new HashMap<>();
        Map<UUID, Norm> norms = new HashMap<>();
        Map<UUID, MaterialInfo> materials = new HashMap<>();
        for (PairingLine pair : masterdata.resolvePairing(date, shift)) {
            Map<UUID, Long> packs = production.packsByProduct(sessionId, pair.packerMachineId());
            BigDecimal ratio = pair.feedRatio() == null ? BigDecimal.ONE : pair.feedRatio();
            for (Map.Entry<UUID, Long> e : packs.entrySet()) {
                Norm norm = norms.computeIfAbsent(e.getKey(), p -> masterdata.activeNorm(p, date).orElse(null));
                if (norm == null) {
                    continue;
                }
                materials.putAll(masterdata.materials(norm.lines().stream().map(NormLine::materialId)
                        .filter(id -> !materials.containsKey(id)).toList()));
                for (NormLine line : norm.lines()) {
                    boolean packing = materials.get(line.materialId()) != null
                            && "PACKING".equals(materials.get(line.materialId()).categoryCode());
                    UUID machine = consumerOf(line, packing, pair);
                    BigDecimal packsForMachine = machine.equals(pair.packerMachineId())
                            ? BigDecimal.valueOf(e.getValue()) : BigDecimal.valueOf(e.getValue()).multiply(ratio);
                    BigDecimal qty = line.qtyPer1000Packs().multiply(packsForMachine)
                            .divide(BigDecimal.valueOf(1000), 6, java.math.RoundingMode.HALF_UP);
                    out.merge(new Key(machine, line.materialId()), qty, BigDecimal::add);
                }
            }
        }
        return out;
    }

    private static UUID consumerOf(NormLine line, boolean packing, PairingLine pair) {
        if ("MAKER".equalsIgnoreCase(line.consumerType())) {
            return pair.makerMachineId();
        }
        if ("PACKER".equalsIgnoreCase(line.consumerType())) {
            return pair.packerMachineId();
        }
        return packing ? pair.packerMachineId() : pair.makerMachineId();
    }

    private BigDecimal sum(String table, String column, Key key, LocalDate date, String shift) {
        BigDecimal v = jdbc.queryForObject("SELECT COALESCE(SUM(" + column + "), 0) FROM " + table
                + " WHERE consumer_machine_id = UUID_TO_BIN(?) AND material_id = UUID_TO_BIN(?) AND business_date = ? "
                + "AND shift_code = ?", BigDecimal.class, key.machineId().toString(), key.materialId().toString(),
                date, shift);
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Kiểm đếm cuối ca; nếu chưa có thì kiểm đếm giữa ca gần nhất; không có → {@code null}. */
    private BigDecimal latestCount(Key key, LocalDate date, String shift) {
        for (String kind : List.of("CLOSING", "MIDSHIFT")) {
            List<BigDecimal> rows = jdbc.queryForList("SELECT counted_qty FROM mt_stock_count WHERE "
                    + "consumer_machine_id = UUID_TO_BIN(?) AND material_id = UUID_TO_BIN(?) AND business_date = ? "
                    + "AND shift_code = ? AND kind = ? ORDER BY counted_at DESC LIMIT 1", BigDecimal.class,
                    key.machineId().toString(), key.materialId().toString(), date, shift, kind);
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
        }
        return null;
    }

    private Map<String, Object> upsert(UUID sessionId, LocalDate date, String shift, Key key, BigDecimal opening,
                                       BigDecimal issued, BigDecimal returned, BigDecimal theo,
                                       BigDecimal threshold, Result r, Instant now) {
        List<Map<String, Object>> existing = jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, status FROM "
                + "mt_consumption_summary WHERE session_id = UUID_TO_BIN(?) AND consumer_machine_id = UUID_TO_BIN(?) "
                + "AND material_id = UUID_TO_BIN(?)", sessionId.toString(), key.machineId().toString(),
                key.materialId().toString());
        String id;
        if (existing.isEmpty()) {
            id = UuidCreator.getTimeOrderedEpoch().toString();
            jdbc.update("INSERT INTO mt_consumption_summary(id, row_version, session_id, business_date, shift_code, "
                    + "consumer_machine_id, material_id, opening_qty, issued_qty, returned_qty, closing_qty, counted, "
                    + "actual_consumed, theoretical_consumed, waste_qty, waste_pct, threshold_pct, flag, status, "
                    + "computed_at) VALUES (UUID_TO_BIN(?), 0, UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), UUID_TO_BIN(?), "
                    + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?)", id, sessionId.toString(), date, shift,
                    key.machineId().toString(), key.materialId().toString(), opening, issued, returned,
                    r.closing(), r.counted(), r.actualConsumed(), theo, r.wasteQty(), r.wastePct(), threshold,
                    r.flag().name(), ts(now));
        } else {
            id = (String) existing.get(0).get("id");
            if ("CONFIRMED".equals(existing.get(0).get("status"))) {
                return summaryOf(UUID.fromString(id));
            }
            jdbc.update("UPDATE mt_consumption_summary SET opening_qty = ?, issued_qty = ?, returned_qty = ?, "
                    + "closing_qty = ?, counted = ?, actual_consumed = ?, theoretical_consumed = ?, waste_qty = ?, "
                    + "waste_pct = ?, threshold_pct = ?, flag = ?, computed_at = ?, row_version = row_version + 1 "
                    + "WHERE id = UUID_TO_BIN(?)", opening, issued, returned, r.closing(), r.counted(),
                    r.actualConsumed(), theo, r.wasteQty(), r.wastePct(), threshold, r.flag().name(), ts(now), id);
        }
        return summaryOf(UUID.fromString(id));
    }

    // ---------- Truy vấn ----------

    private Map<String, Object> summaryOf(UUID id) {
        return jdbc.queryForList(summarySql() + " WHERE s.id = UUID_TO_BIN(?)", id.toString()).get(0);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> summaries(UUID sessionId) {
        return jdbc.queryForList(summarySql() + " WHERE s.session_id = UUID_TO_BIN(?) ORDER BY "
                + "s.consumer_machine_id, s.material_id", sessionId.toString());
    }

    private static String summarySql() {
        return "SELECT BIN_TO_UUID(s.id) AS id, BIN_TO_UUID(s.session_id) AS sessionId, BIN_TO_UUID("
                + "s.consumer_machine_id) AS machineId, BIN_TO_UUID(s.material_id) AS materialId, s.opening_qty AS "
                + "openingQty, s.issued_qty AS issuedQty, s.returned_qty AS returnedQty, s.closing_qty AS "
                + "closingQty, s.counted, s.actual_consumed AS actualConsumed, s.theoretical_consumed AS "
                + "theoreticalConsumed, s.waste_qty AS wasteQty, s.waste_pct AS wastePct, s.threshold_pct AS "
                + "thresholdPct, s.flag, s.explanation, s.status FROM mt_consumption_summary s";
    }

    /** FR-MT-009: ghi giải trình cho dòng tiêu hao có cờ WARN/CRIT trước khi chốt. */
    @Transactional
    public Map<String, Object> explain(UUID summaryId, String text) {
        if (text == null || text.trim().length() < 3) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cần nhập giải trình (tối thiểu 3 ký tự)",
                    Map.of("explanation", List.of("Vui lòng nhập giải trình")));
        }
        int updated = jdbc.update("UPDATE mt_consumption_summary SET explanation = ?, row_version = row_version + 1 "
                + "WHERE id = UUID_TO_BIN(?) AND status = 'DRAFT'", text.trim(), summaryId.toString());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Không tìm thấy dòng tiêu hao hoặc đã được chốt");
        }
        audit.write("ConsumptionSummary", summaryId, "EXPLAIN", List.of(), text);
        Map<String, Object> summary = summaryOf(summaryId);
        production.session(UUID.fromString((String) summary.get("sessionId"))).ifPresent(this::publishChanged);
        return summary;
    }

    /** Chốt tiêu hao ca: không còn tranh chấp/khung trễ; dòng vượt ngưỡng đã có giải trình (BR-MT-005/007). */
    @Transactional
    public List<Map<String, Object>> confirm(UUID sessionId) {
        SessionRef session = production.session(sessionId).orElseThrow(() -> new BusinessException(
                ErrorCode.NOT_FOUND, "Không tìm thấy phiên ca"));
        if ("OPEN".equals(session.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Chỉ chốt tiêu hao khi ca đã kết thúc");
        }
        List<Map<String, Object>> rows = compute(sessionId);
        List<String> problems = new ArrayList<>();
        disputed(session.businessDate(), session.shiftCode()).forEach(d -> problems.add("Tranh chấp nhận NVL: "
                + d.get("id")));
        lateLines(session.businessDate(), session.shiftCode(), clock.instant()).forEach(l -> problems.add(
                "Khung " + l.get("slotNo") + " chưa giao nhận xong: " + l.get("reason")));
        for (Map<String, Object> r : rows) {
            if (!"OK".equals(r.get("flag")) && r.get("explanation") == null) {
                problems.add("Hao hụt " + r.get("flag") + " chưa giải trình: máy " + r.get("machineId")
                        + ", NVL " + r.get("materialId"));
            }
        }
        if (!problems.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Chưa thể chốt tiêu hao ca: "
                    + problems.size() + " vấn đề", Map.of("problems", problems));
        }
        UUID userId = currentUser.require().userId();
        jdbc.update("UPDATE mt_consumption_summary SET status = 'CONFIRMED', confirmed_by = UUID_TO_BIN(?), "
                + "confirmed_at = ?, row_version = row_version + 1 WHERE session_id = UUID_TO_BIN(?) AND status = "
                + "'DRAFT'", userId.toString(), ts(clock.instant()), sessionId.toString());
        audit.write("ConsumptionSummary", sessionId, "CONFIRM_SHIFT", List.of(new FieldChange("rows", null,
                String.valueOf(rows.size()))), null);
        publishChanged(session);
        return summaries(sessionId);
    }

    /** Báo cáo tiêu hao ca (FR-MT-009, AC-MT-006): dữ liệu JSON; xuất PDF/Excel làm cùng RP. */
    @Transactional(readOnly = true)
    public Map<String, Object> report(UUID sessionId) {
        SessionRef session = production.session(sessionId).orElseThrow(() -> new BusinessException(
                ErrorCode.NOT_FOUND, "Không tìm thấy phiên ca"));
        List<Map<String, Object>> rows = summaries(sessionId);
        Map<UUID, MaterialInfo> materials = masterdata.materials(rows.stream()
                .map(r -> UUID.fromString((String) r.get("materialId"))).distinct().toList());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> l = new LinkedHashMap<>(r);
            MaterialInfo m = materials.get(UUID.fromString((String) r.get("materialId")));
            l.put("materialCode", m == null ? null : m.code());
            l.put("materialName", m == null ? null : m.name());
            l.put("category", m == null ? null : m.categoryCode());
            lines.add(l);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", sessionId.toString());
        out.put("businessDate", session.businessDate());
        out.put("shiftCode", session.shiftCode());
        out.put("lines", lines);
        return out;
    }

    // ---------- Giao nhận trễ / tranh chấp ----------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> disputed(LocalDate date, String shift) {
        return jdbc.queryForList("SELECT BIN_TO_UUID(c.id) AS id, BIN_TO_UUID(r.consumer_machine_id) AS machineId, "
                + "BIN_TO_UUID(r.material_id) AS materialId FROM mt_receipt_confirmation c JOIN mt_issue_record r ON "
                + "r.id = c.issue_record_id WHERE c.status = 'DISPUTED' AND r.business_date = ? AND r.shift_code = ?",
                date, shift);
    }

    /**
     * BR-MT-005: dòng phiếu đã xác nhận có khung giờ đã qua + dung sai mà chưa có cấp phát hoặc còn bản ghi cấp
     * chưa có xác nhận nhận.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> lateLines(LocalDate date, String shift, Instant now) {
        Optional<MasterdataQueries.ShiftWindow> window = masterdata.shift(shift);
        List<IssueSlot> slots = masterdata.issueSlots(shift);
        if (window.isEmpty() || slots.isEmpty()) {
            return List.of();
        }
        int tolerance = masterdata.resolveThreshold("MT_SLOT_TOLERANCE_MIN", null, null, null, null, null, date)
                .map(t -> t.value().intValue()).orElse(DEFAULT_TOLERANCE_MIN);
        Map<Integer, Instant> slotEnd = new HashMap<>();
        LocalTime shiftStart = window.get().start();
        for (IssueSlot slot : slots) {
            if (slot.end() == null) {
                continue;
            }
            LocalDate endDate = slot.end().isAfter(shiftStart) ? date : date.plusDays(1);
            slotEnd.put(slot.slotNo(), endDate.atTime(slot.end()).atZone(BUSINESS_ZONE).toInstant()
                    .plusSeconds(tolerance * 60L));
        }
        List<Map<String, Object>> late = new ArrayList<>();
        List<Map<String, Object>> lines = jdbc.queryForList("SELECT BIN_TO_UUID(l.id) AS lineId, l.slot_no AS "
                + "slotNo, BIN_TO_UUID(s.consumer_machine_id) AS machineId, BIN_TO_UUID(l.material_id) AS "
                + "materialId, (SELECT COUNT(*) FROM mt_issue_record r WHERE r.slip_line_id = l.id) AS issues, "
                + "(SELECT COUNT(*) FROM mt_issue_record r WHERE r.slip_line_id = l.id AND NOT EXISTS (SELECT 1 FROM "
                + "mt_receipt_confirmation c WHERE c.issue_record_id = r.id)) AS unreceived FROM mt_issue_slip_line l "
                + "JOIN mt_issue_slip s ON s.id = l.slip_id WHERE s.business_date = ? AND s.shift_code = ? AND "
                + "s.status IN ('CONFIRMED','ISSUING','COMPLETED') AND l.planned_qty > 0", date, shift);
        for (Map<String, Object> l : lines) {
            Instant deadline = slotEnd.get(((Number) l.get("slotNo")).intValue());
            if (deadline == null || !now.isAfter(deadline)) {
                continue;
            }
            long issues = ((Number) l.get("issues")).longValue();
            long unreceived = ((Number) l.get("unreceived")).longValue();
            if (issues == 0 || unreceived > 0) {
                Map<String, Object> m = new LinkedHashMap<>(l);
                m.put("reason", issues == 0 ? "chưa cấp phát" : "chưa xác nhận nhận");
                late.add(m);
            }
        }
        return late;
    }
}
