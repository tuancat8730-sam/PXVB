package vn.pxvb.production.application;

import static vn.pxvb.production.application.ProductionTime.instant;
import static vn.pxvb.production.application.ProductionTime.ts;

import com.github.f4b6a3.uuid.UuidCreator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.alerting.contract.AlertRaiser;
import vn.pxvb.alerting.contract.AlertRaiser.AlertCommand;
import vn.pxvb.common.audit.AuditLogWriter;
import vn.pxvb.common.audit.AuditLogWriter.FieldChange;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.masterdata.contract.MasterdataQueries;
import vn.pxvb.masterdata.contract.MasterdataQueries.MachineInfo;
import vn.pxvb.masterdata.contract.MasterdataQueries.MachineRate;
import vn.pxvb.production.contract.ProductionEvents.ShiftEnded;
import vn.pxvb.production.domain.OeeCalculator;
import vn.pxvb.production.domain.OeeCalculator.Formula;
import vn.pxvb.production.domain.OeeCalculator.Input;
import vn.pxvb.production.domain.OeeCalculator.PlannedTimeMode;
import vn.pxvb.production.domain.OeeCalculator.QualityMode;
import vn.pxvb.production.domain.OeeCalculator.Result;

/**
 * OEE theo máy/ca (FR-PR-008): tạm tính trong ca, chính thức khi chốt ca và các dừng kỹ thuật đã xác nhận.
 * Công thức cấu hình có phiên bản ({@code pr_oee_formula_config}); gộp ngày/tuần/tháng cộng dồn tử – mẫu.
 */
@Service
public class OeeService {

    private static final BigDecimal DEFAULT_LOW_PCT = BigDecimal.valueOf(60);
    private static final int MIN_ELAPSED_MINUTES = 60;

    public record FormulaConfig(UUID id, int versionNo, Formula formula) {
    }

    private final JdbcTemplate jdbc;
    private final MasterdataQueries masterdata;
    private final AlertRaiser alerts;
    private final AuditLogWriter audit;
    private final CurrentUser currentUser;
    private final Clock clock;

    public OeeService(JdbcTemplate jdbc, MasterdataQueries masterdata, AlertRaiser alerts, AuditLogWriter audit,
                      CurrentUser currentUser, Clock clock) {
        this.jdbc = jdbc;
        this.masterdata = masterdata;
        this.alerts = alerts;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // ---------- Công thức ----------

    @Transactional(readOnly = true)
    public FormulaConfig currentFormula() {
        return jdbc.query("SELECT BIN_TO_UUID(id) AS id, version_no, planned_time_mode, quality_mode, "
                + "cap_performance_at_100 FROM pr_oee_formula_config WHERE effective_from <= ? "
                + "ORDER BY version_no DESC LIMIT 1", (rs, i) -> new FormulaConfig(
                UUID.fromString(rs.getString("id")), rs.getInt("version_no"), new Formula(
                PlannedTimeMode.valueOf(rs.getString("planned_time_mode")),
                QualityMode.valueOf(rs.getString("quality_mode")), rs.getBoolean("cap_performance_at_100"))),
                ts(clock.instant())).stream().findFirst().orElseThrow(() -> new BusinessException(
                ErrorCode.INTERNAL, "Chưa có công thức OEE"));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> formulaView() {
        FormulaConfig c = currentFormula();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", c.id().toString());
        out.put("versionNo", c.versionNo());
        out.put("plannedTimeMode", c.formula().plannedTimeMode().name());
        out.put("qualityMode", c.formula().qualityMode().name());
        out.put("capPerformanceAt100", c.formula().capPerformanceAt100());
        return out;
    }

    /** Tạo phiên bản công thức mới có hiệu lực ngay; kết quả đã tính giữ nguyên phiên bản cũ đến khi tính lại. */
    @Transactional
    public Map<String, Object> updateFormula(String plannedTimeMode, String qualityMode, boolean cap, String note) {
        PlannedTimeMode planned;
        QualityMode quality;
        try {
            planned = PlannedTimeMode.valueOf(plannedTimeMode);
            quality = QualityMode.valueOf(qualityMode);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(ErrorCode.VALIDATION, "Chế độ thời gian kế hoạch/chất lượng không hợp lệ");
        }
        FormulaConfig previous = currentFormula();
        Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(version_no), 0) + 1 FROM pr_oee_formula_config",
                Integer.class);
        UUID id = UuidCreator.getTimeOrderedEpoch();
        UUID userId = currentUser.userId().orElse(null);
        Instant now = clock.instant();
        jdbc.update("INSERT INTO pr_oee_formula_config(id, row_version, created_at, created_by, version_no, "
                + "planned_time_mode, quality_mode, cap_performance_at_100, effective_from, note) VALUES ("
                + "UUID_TO_BIN(?), 0, ?, UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?)", id.toString(), ts(now),
                userId == null ? null : userId.toString(), next, planned.name(), quality.name(), cap, ts(now), note);
        audit.write(new AuditLogWriter.Entry("pr", "OeeFormulaConfig", id.toString(), "UPDATE", List.of(
                new FieldChange("versionNo", String.valueOf(previous.versionNo()), String.valueOf(next)),
                new FieldChange("plannedTimeMode", previous.formula().plannedTimeMode().name(), planned.name()),
                new FieldChange("qualityMode", previous.formula().qualityMode().name(), quality.name()),
                new FieldChange("capPerformanceAt100", String.valueOf(previous.formula().capPerformanceAt100()),
                        String.valueOf(cap))), note, userId, null,
                currentUser.deviceId().orElse(null), null));
        return formulaView();
    }

    // ---------- Tính ----------

    /** Kết ca → tính OEE (vẫn tạm tính cho đến khi chốt ca và xác nhận đủ dừng kỹ thuật). */
    @EventListener
    @Transactional
    public void onShiftEnded(ShiftEnded event) {
        computeSession(event.sessionId());
    }

    /** Tính lại OEE mọi máy có lệnh của phiên ca; trả về các dòng đã lưu. */
    @Transactional
    public List<Map<String, Object>> computeSession(UUID sessionId) {
        List<Map<String, Object>> sessions = jdbc.queryForList("SELECT business_date, shift_code, status, "
                + "actual_end FROM pr_shift_session WHERE id = UUID_TO_BIN(?)", sessionId.toString());
        if (sessions.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên ca");
        }
        Map<String, Object> s = sessions.get(0);
        LocalDate date = ((java.sql.Date) s.get("business_date")).toLocalDate();
        String shift = (String) s.get("shift_code");
        String status = (String) s.get("status");
        Instant now = clock.instant();
        Instant asOf = s.get("actual_end") == null ? now : instant(s.get("actual_end"));
        FormulaConfig config = currentFormula();

        List<Map<String, Object>> runs = jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, BIN_TO_UUID(machine_id) "
                + "AS machineId, planned_minutes, output_qty FROM pr_machine_run WHERE session_id = UUID_TO_BIN(?) "
                + "AND status <> 'NO_ORDER'", sessionId.toString());
        List<UUID> machineIds = runs.stream().map(r -> UUID.fromString((String) r.get("machineId"))).toList();
        Map<UUID, MachineRate> rates = masterdata.machineRates(machineIds);
        boolean techPending = hasUnconfirmedTechStops(sessionId);
        boolean provisional = !("CLOSED".equals(status) || "FINALIZED".equals(status)) || techPending;

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> run : runs) {
            UUID runId = UUID.fromString((String) run.get("id"));
            UUID machineId = UUID.fromString((String) run.get("machineId"));
            int[] stops = RecordingService.stopMinutes(jdbc, runId, asOf);
            long total = ((Number) run.get("output_qty")).longValue();
            MachineRate rate = rates.get(machineId);
            Result r = OeeCalculator.compute(config.formula(), new Input(((Number) run.get("planned_minutes"))
                    .intValue(), stops[0], stops[1], total, total, rate == null ? null : rate.perMinute()));
            String unit = rate == null ? unitOfMachine(machineId) : rate.unit();
            jdbc.update("INSERT INTO pr_oee_result(id, row_version, run_id, session_id, machine_id, business_date, "
                    + "shift_code, unit, planned_minutes, run_minutes, total_qty, good_qty, theoretical_qty, "
                    + "availability, performance, quality, oee, is_provisional, formula_id, computed_at) VALUES ("
                    + "UUID_TO_BIN(?), 0, UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, ?, ?, "
                    + "?, ?, ?, ?, ?, UUID_TO_BIN(?), ?) ON DUPLICATE KEY UPDATE planned_minutes = VALUES("
                    + "planned_minutes), run_minutes = VALUES(run_minutes), total_qty = VALUES(total_qty), "
                    + "good_qty = VALUES(good_qty), theoretical_qty = VALUES(theoretical_qty), availability = "
                    + "VALUES(availability), performance = VALUES(performance), quality = VALUES(quality), "
                    + "oee = VALUES(oee), is_provisional = VALUES(is_provisional), formula_id = VALUES(formula_id), "
                    + "computed_at = VALUES(computed_at), row_version = row_version + 1",
                    UuidCreator.getTimeOrderedEpoch().toString(), runId.toString(), sessionId.toString(),
                    machineId.toString(), date, shift, unit, r.plannedMinutes(), r.runMinutes(), r.totalQty(),
                    r.goodQty(), r.theoreticalQty(), r.availability(), r.performance(), r.quality(), r.oee(),
                    provisional, config.id().toString(), ts(now));
            out.add(row(machineId, date, shift, unit, r, provisional));
        }
        return out;
    }

    private String unitOfMachine(UUID machineId) {
        MachineInfo machine = masterdata.machines(List.of(machineId)).get(machineId);
        return machine != null && "MAKER".equals(machine.machineType()) ? "RODS" : "PACKS";
    }

    private boolean hasUnconfirmedTechStops(UUID sessionId) {
        Set<String> codes = new HashSet<>();
        jdbc.query("SELECT DISTINCT s.reason_code FROM pr_stop_event s JOIN pr_machine_run r ON r.id = s.run_id "
                + "WHERE r.session_id = UUID_TO_BIN(?) AND s.tech_confirmed_at IS NULL", rs -> {
            codes.add(rs.getString(1));
        }, sessionId.toString());
        return codes.stream().anyMatch(c -> masterdata.stopReason(c).map(sr -> sr.requiresTechConfirm())
                .orElse(false));
    }

    /** Tính lại rồi phát {@code PR_OEE_LOW} cho máy dưới ngưỡng (sau ≥ 60' từ giờ vào ca). */
    @Transactional
    public int evaluateLow(UUID sessionId, LocalDate date, Instant startedAt, Instant now) {
        List<Map<String, Object>> rows = computeSession(sessionId);
        if (java.time.Duration.between(startedAt, now).toMinutes() < MIN_ELAPSED_MINUTES) {
            return 0;
        }
        int raised = 0;
        for (Map<String, Object> row : rows) {
            Object oee = row.get("oee");
            if (oee == null || ((Number) row.get("totalQty")).longValue() == 0) {
                continue; // chưa có sản lượng hoặc N/A → không đánh giá
            }
            UUID machineId = UUID.fromString((String) row.get("machineId"));
            BigDecimal limit = masterdata.resolveThreshold("PR_OEE_LOW_PCT", machineId, null, null, null, null, date)
                    .map(t -> t.value()).orElse(DEFAULT_LOW_PCT);
            BigDecimal pct = ((BigDecimal) oee).multiply(BigDecimal.valueOf(100));
            if (pct.compareTo(limit) < 0) {
                alerts.raise(new AlertCommand("PR_OEE_LOW", "Machine", machineId.toString(), machineId, sessionId,
                        "OEE tạm tính thấp", "OEE " + pct.setScale(1, RoundingMode.HALF_UP) + "% < ngưỡng "
                        + limit.stripTrailingZeros().toPlainString() + "%", Map.of("oeePct",
                        pct.setScale(2, RoundingMode.HALF_UP)), null, null));
                raised++;
            }
        }
        return raised;
    }

    // ---------- Truy vấn ----------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> sessionOee(UUID sessionId) {
        return jdbc.query(selectSql() + " WHERE session_id = UUID_TO_BIN(?) ORDER BY machine_id", (rs, i) ->
                fromRow(rs), sessionId.toString());
    }

    /**
     * OEE theo kỳ. {@code grain}: SHIFT | DAY | WEEK | MONTH; mỗi nhóm (máy, kỳ) gộp cộng dồn tử – mẫu; thêm
     * dòng tổng {@code machineId = null} theo từng kỳ.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> query(String grain, LocalDate from, LocalDate to, UUID machineId, String model) {
        String g = grain == null ? "DAY" : grain.toUpperCase();
        if (!Set.of("SHIFT", "DAY", "WEEK", "MONTH").contains(g)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Độ chi tiết phải là SHIFT, DAY, WEEK hoặc MONTH");
        }
        LocalDate end = to == null ? LocalDate.now(ProductionTime.BUSINESS_ZONE) : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        if (end.isBefore(start)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Khoảng thời gian không hợp lệ");
        }
        StringBuilder sql = new StringBuilder(selectSql()).append(" WHERE business_date BETWEEN ? AND ?");
        List<Object> args = new ArrayList<>(List.of(start, end));
        if (machineId != null) {
            sql.append(" AND machine_id = UUID_TO_BIN(?)");
            args.add(machineId.toString());
        }
        List<Map<String, Object>> rows = jdbc.query(sql.toString(), (rs, i) -> fromRow(rs), args.toArray());
        if (model != null && !model.isBlank()) {
            Map<UUID, MachineInfo> machines = masterdata.machines(rows.stream()
                    .map(r -> UUID.fromString((String) r.get("machineId"))).distinct().toList());
            rows = rows.stream().filter(r -> {
                MachineInfo m = machines.get(UUID.fromString((String) r.get("machineId")));
                return m != null && model.equalsIgnoreCase(m.model());
            }).toList();
        }
        FormulaConfig config = currentFormula();
        Map<String, List<Map<String, Object>>> groups = new TreeMap<>();
        for (Map<String, Object> r : rows) {
            LocalDate d = (LocalDate) r.get("businessDate");
            String key = periodKey(g, d, (String) r.get("shiftCode"));
            groups.computeIfAbsent(key + "#" + r.get("machineId"), k -> new ArrayList<>()).add(r);
            groups.computeIfAbsent(key + "#", k -> new ArrayList<>()).add(r);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : groups.entrySet()) {
            String[] parts = e.getKey().split("#", -1);
            List<Result> results = new ArrayList<>();
            boolean provisional = false;
            for (Map<String, Object> r : e.getValue()) {
                results.add(new Result((Integer) r.get("plannedMinutes"), (Integer) r.get("runMinutes"),
                        ((Number) r.get("totalQty")).longValue(), ((Number) r.get("goodQty")).longValue(),
                        (BigDecimal) r.get("theoreticalQty"), null, null, null, null));
                provisional |= Boolean.TRUE.equals(r.get("isProvisional"));
            }
            Result agg = OeeCalculator.aggregate(config.formula(), results);
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("grain", g);
            o.put("period", parts[0]);
            o.put("machineId", parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null);
            o.put("plannedMinutes", agg.plannedMinutes());
            o.put("runMinutes", agg.runMinutes());
            o.put("totalQty", agg.totalQty());
            o.put("goodQty", agg.goodQty());
            o.put("availability", agg.availability());
            o.put("performance", agg.performance());
            o.put("quality", agg.quality());
            o.put("oee", agg.oee());
            o.put("oeePct", pct(agg.oee()));
            o.put("isProvisional", provisional);
            out.add(o);
        }
        return out;
    }

    private static String periodKey(String grain, LocalDate date, String shift) {
        return switch (grain) {
            case "SHIFT" -> date + "|" + shift;
            case "WEEK" -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
            case "MONTH" -> date.toString().substring(0, 7);
            default -> date.toString();
        };
    }

    private static String selectSql() {
        return "SELECT BIN_TO_UUID(machine_id) AS machine_id, business_date, shift_code, unit, planned_minutes, "
                + "run_minutes, total_qty, good_qty, theoretical_qty, availability, performance, quality, oee, "
                + "is_provisional FROM pr_oee_result";
    }

    private static Map<String, Object> fromRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = new HashMap<>();
        m.put("machineId", rs.getString("machine_id"));
        m.put("businessDate", rs.getDate("business_date").toLocalDate());
        m.put("shiftCode", rs.getString("shift_code"));
        m.put("unit", rs.getString("unit"));
        m.put("plannedMinutes", rs.getInt("planned_minutes"));
        m.put("runMinutes", rs.getInt("run_minutes"));
        m.put("totalQty", rs.getLong("total_qty"));
        m.put("goodQty", rs.getLong("good_qty"));
        m.put("theoreticalQty", rs.getBigDecimal("theoretical_qty"));
        m.put("availability", rs.getBigDecimal("availability"));
        m.put("performance", rs.getBigDecimal("performance"));
        m.put("quality", rs.getBigDecimal("quality"));
        m.put("oee", rs.getBigDecimal("oee"));
        m.put("oeePct", pct(rs.getBigDecimal("oee")));
        m.put("isProvisional", rs.getBoolean("is_provisional"));
        return m;
    }

    private static Map<String, Object> row(UUID machineId, LocalDate date, String shift, String unit, Result r,
                                           boolean provisional) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("machineId", machineId.toString());
        m.put("businessDate", date);
        m.put("shiftCode", shift);
        m.put("unit", unit);
        m.put("plannedMinutes", r.plannedMinutes());
        m.put("runMinutes", r.runMinutes());
        m.put("totalQty", r.totalQty());
        m.put("goodQty", r.goodQty());
        m.put("theoreticalQty", r.theoreticalQty());
        m.put("availability", r.availability());
        m.put("performance", r.performance());
        m.put("quality", r.quality());
        m.put("oee", r.oee());
        m.put("oeePct", pct(r.oee()));
        m.put("isProvisional", provisional);
        return m;
    }

    private static BigDecimal pct(BigDecimal ratio) {
        return ratio == null ? null : ratio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }
}
