package vn.pxvb.production.application;

import static vn.pxvb.production.application.ProductionTime.BUSINESS_ZONE;
import static vn.pxvb.production.application.ProductionTime.instant;
import static vn.pxvb.production.application.ProductionTime.ts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.audit.AuditLogWriter;
import vn.pxvb.common.audit.AuditLogWriter.FieldChange;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.common.outbox.OutboxPublisher;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.masterdata.contract.MasterdataQueries;
import vn.pxvb.masterdata.contract.MasterdataQueries.MachineInfo;
import vn.pxvb.masterdata.contract.MasterdataQueries.ShiftWindow;
import vn.pxvb.planning.contract.PlanningQueries;
import vn.pxvb.planning.contract.PlanningQueries.OrderRef;
import vn.pxvb.planning.contract.PlanningQueries.RosterRef;
import vn.pxvb.production.contract.ProductionEvents.ShiftEnded;
import vn.pxvb.production.contract.ProductionEvents.ShiftOpened;

/**
 * Phiên ca (FR-PR-001): mở ca tạo {@code MachineRun} cho toàn bộ máy đang hoạt động (AC-PR-001); kết ca đóng dừng
 * máy đang mở, tính phút chạy/dừng và chuyển phiên sang {@code CLOSING} để chốt ca (CC-11, E14) xử lý tiếp.
 */
@Service
public class ShiftSessionService {

    static final int DEFAULT_OUTPUT_INTERVAL_MIN = 120;

    private final JdbcTemplate jdbc;
    private final MasterdataQueries masterdata;
    private final PlanningQueries planning;
    private final OutboxPublisher outbox;
    private final ApplicationEventPublisher events;
    private final AuditLogWriter audit;
    private final CurrentUser currentUser;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ShiftSessionService(JdbcTemplate jdbc, MasterdataQueries masterdata, PlanningQueries planning,
                               OutboxPublisher outbox, ApplicationEventPublisher events, AuditLogWriter audit,
                               CurrentUser currentUser, ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.masterdata = masterdata;
        this.planning = planning;
        this.outbox = outbox;
        this.events = events;
        this.audit = audit;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> open(LocalDate businessDate, String shiftCode) {
        if (businessDate == null || shiftCode == null || shiftCode.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cần ngày sản xuất và mã ca");
        }
        String shift = shiftCode.toUpperCase();
        ShiftWindow window = masterdata.shift(shift).orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION,
                "Ca " + shift + " chưa được khai báo hoặc đã ngừng sử dụng"));
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM pr_shift_session WHERE business_date = ? "
                + "AND shift_code = ?", Integer.class, businessDate, shift);
        if (existing != null && existing > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Ca " + shift + " ngày " + businessDate + " đã có phiên ca");
        }
        // BR-PR-008: ca vắt ngày lấy BusinessDate = ngày bắt đầu ca
        Instant plannedStart = businessDate.atTime(window.start()).atZone(BUSINESS_ZONE).toInstant();
        LocalDate endDate = window.crossesMidnight() || !window.end().isAfter(window.start())
                ? businessDate.plusDays(1) : businessDate;
        Instant plannedEnd = endDate.atTime(window.end()).atZone(BUSINESS_ZONE).toInstant();
        int plannedMinutes = (int) java.time.Duration.between(plannedStart, plannedEnd).toMinutes();

        List<String> warnings = new ArrayList<>();
        RosterRef roster = planning.roster(businessDate, shift).orElse(null);
        if (roster == null || !"PUBLISHED".equals(roster.status())) {
            warnings.add("Chưa công bố bố trí nhân sự cho ca này");
        }
        List<OrderRef> orders = planning.activeOrders(businessDate, shift);
        if (orders.isEmpty()) {
            warnings.add("Chưa có lệnh sản xuất được xác nhận cho ca này");
        }

        UUID userId = currentUser.userId().orElse(null);
        UUID sessionId = UuidCreator.getTimeOrderedEpoch();
        Instant now = clock.instant();
        jdbc.update("INSERT INTO pr_shift_session(id, row_version, created_at, created_by, updated_at, updated_by, "
                        + "business_date, shift_code, planned_start, planned_end, actual_start, status, "
                        + "leader_employee_id, opened_by, warnings) VALUES (UUID_TO_BIN(?), 0, ?, UUID_TO_BIN(?), "
                        + "?, UUID_TO_BIN(?), ?, ?, ?, ?, ?, 'OPEN', UUID_TO_BIN(?), UUID_TO_BIN(?), ?)",
                sessionId.toString(), ts(now), bin(userId), ts(now), bin(userId), businessDate, shift,
                ts(plannedStart), ts(plannedEnd), ts(now),
                roster == null || roster.leaderEmployeeId() == null ? null : bin(roster.leaderEmployeeId()),
                bin(userId), json(warnings));

        Map<UUID, OrderRef> byMachine = new HashMap<>();
        for (OrderRef order : orders) {
            byMachine.put(order.makerMachineId(), order);
            byMachine.put(order.packerMachineId(), order);
        }
        List<MachineInfo> machines = masterdata.activeMachines();
        List<Object[]> rows = new ArrayList<>(machines.size());
        for (MachineInfo machine : machines) {
            OrderRef order = byMachine.get(machine.id());
            rows.add(new Object[]{UuidCreator.getTimeOrderedEpoch().toString(), sessionId.toString(),
                    machine.id().toString(), order == null ? null : order.orderId().toString(),
                    order == null ? null : order.code(), order == null ? "NO_ORDER" : "NOT_STARTED",
                    order == null ? null : order.plannedPacks(),
                    order == null ? null : order.productId().toString(),
                    order == null ? null : order.productId().toString(), plannedMinutes});
        }
        jdbc.batchUpdate("INSERT INTO pr_machine_run(id, session_id, machine_id, order_id, order_code, status, "
                + "planned_packs, initial_product_id, current_product_id, planned_minutes) VALUES ("
                + "UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, UUID_TO_BIN(?), "
                + "UUID_TO_BIN(?), ?)", rows);
        orders.forEach(o -> planning.markRunning(o.orderId()));

        audit(sessionId, "OPEN", List.of(new FieldChange("status", null, "OPEN"),
                new FieldChange("machineRuns", null, String.valueOf(machines.size()))));
        events.publishEvent(new ShiftOpened(sessionId, businessDate, shift, plannedStart, plannedEnd));
        outbox.publish("pr.session.opened", Map.of("sessionId", sessionId.toString(),
                "businessDate", businessDate.toString(), "shiftCode", shift, "machineRuns", machines.size()));
        return get(sessionId);
    }

    @Transactional
    public Map<String, Object> end(UUID sessionId) {
        Map<String, Object> session = lockOpen(sessionId);
        Instant now = clock.instant();
        Instant plannedEnd = instant(session.get("plannedEndTs"));
        Instant closeAt = now.isBefore(plannedEnd) ? now : plannedEnd;

        // BR-PR-002: dừng đang mở lúc hết ca tự đóng tại giờ kết thúc ca, đánh dấu AutoClosed
        List<Map<String, Object>> openStops = jdbc.queryForList("SELECT BIN_TO_UUID(s.id) AS id, "
                + "BIN_TO_UUID(s.machine_id) AS machineId, s.reason_code AS reasonCode, s.start_at AS startAt "
                + "FROM pr_stop_event s JOIN pr_machine_run r ON r.id = s.run_id WHERE r.session_id = "
                + "UUID_TO_BIN(?) AND s.end_at IS NULL", sessionId.toString());
        for (Map<String, Object> stop : openStops) {
            Instant startAt = instant(stop.get("startAt"));
            Instant end = closeAt.isBefore(startAt) ? startAt : closeAt;
            jdbc.update("UPDATE pr_stop_event SET end_at = ?, auto_closed = 1, row_version = row_version + 1 "
                    + "WHERE id = UUID_TO_BIN(?)", ts(end), stop.get("id"));
        }
        List<Map<String, Object>> runs = jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, status, planned_minutes "
                + "AS plannedMinutes FROM pr_machine_run WHERE session_id = UUID_TO_BIN(?)", sessionId.toString());
        for (Map<String, Object> run : runs) {
            UUID runId = UUID.fromString((String) run.get("id"));
            int[] stops = RecordingService.stopMinutes(jdbc, runId, closeAt);
            int planned = ((Number) run.get("plannedMinutes")).intValue();
            int runMinutes = Math.max(0, planned - stops[0] - stops[1]);
            jdbc.update("UPDATE pr_machine_run SET planned_stop_minutes = ?, unplanned_stop_minutes = ?, "
                    + "run_minutes = ?, status = IF(status = 'NO_ORDER', status, 'ENDED'), "
                    + "row_version = row_version + 1 WHERE id = UUID_TO_BIN(?)", stops[0], stops[1], runMinutes,
                    runId.toString());
        }
        planning.activeOrders(((java.sql.Date) session.get("businessDate")).toLocalDate(),
                (String) session.get("shiftCode")).forEach(o -> planning.markCompleted(o.orderId()));

        UUID userId = currentUser.userId().orElse(null);
        jdbc.update("UPDATE pr_shift_session SET status = 'CLOSING', actual_end = ?, closed_by = UUID_TO_BIN(?), "
                + "row_version = row_version + 1, updated_at = ?, updated_by = UUID_TO_BIN(?) "
                + "WHERE id = UUID_TO_BIN(?)",
                ts(now), bin(userId), ts(now), bin(userId), sessionId.toString());
        LocalDate date = ((java.sql.Date) session.get("businessDate")).toLocalDate();
        String shift = (String) session.get("shiftCode");
        audit(sessionId, "END", List.of(new FieldChange("status", "OPEN", "CLOSING"),
                new FieldChange("autoClosedStops", null, String.valueOf(openStops.size()))));
        events.publishEvent(new ShiftEnded(sessionId, date, shift, now));
        outbox.publish("pr.session.ended", Map.of("sessionId", sessionId.toString(), "businessDate",
                date.toString(), "shiftCode", shift));
        Map<String, Object> result = new java.util.LinkedHashMap<>(get(sessionId));
        result.put("autoClosedStops", openStops.size());
        result.put("missingOutput", missingOutput(sessionId, plannedMinutesOf(runs)));
        return result;
    }

    /** BR-PR-005: máy (có lệnh) chưa đủ số lần ghi sản lượng tối thiểu. */
    public List<Map<String, Object>> missingOutput(UUID sessionId, int shiftMinutes) {
        int interval = DEFAULT_OUTPUT_INTERVAL_MIN;
        int required = Math.max(1, shiftMinutes / interval);
        return jdbc.queryForList("SELECT BIN_TO_UUID(r.machine_id) AS machineId, r.output_count AS outputCount, "
                + "? AS required FROM pr_machine_run r WHERE r.session_id = UUID_TO_BIN(?) AND r.status <> "
                + "'NO_ORDER' AND r.output_count < ?", required, sessionId.toString(), required);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(sessionSql() + " WHERE s.id = UUID_TO_BIN(?)",
                id.toString());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên ca");
        }
        return view(rows.get(0));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(LocalDate date, String shift, String status) {
        StringBuilder sql = new StringBuilder(sessionSql()).append(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (date != null) {
            sql.append(" AND s.business_date = ?");
            args.add(date);
        }
        if (shift != null && !shift.isBlank()) {
            sql.append(" AND s.shift_code = ?");
            args.add(shift.toUpperCase());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND s.status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY s.business_date DESC, s.shift_code");
        return jdbc.queryForList(sql.toString(), args.toArray()).stream().map(ShiftSessionService::view).toList();
    }

    private static String sessionSql() {
        return "SELECT BIN_TO_UUID(s.id) AS id, s.business_date AS businessDate, s.shift_code AS shiftCode, "
                + "s.planned_start AS plannedStartTs, s.planned_end AS plannedEndTs, s.actual_start AS "
                + "actualStartTs, s.actual_end AS actualEndTs, s.status, BIN_TO_UUID(s.leader_employee_id) AS "
                + "leaderEmployeeId, BIN_TO_UUID(s.opened_by) AS openedBy, BIN_TO_UUID(s.closed_by) AS closedBy, "
                + "s.warnings, s.row_version AS rowVersion FROM pr_shift_session s";
    }

    private static Map<String, Object> view(Map<String, Object> row) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        row.forEach((k, v) -> {
            if (k.endsWith("Ts")) {
                out.put(k.substring(0, k.length() - 2), instant(v));
            } else {
                out.put(k, v);
            }
        });
        return out;
    }

    private Map<String, Object> lockOpen(UUID sessionId) {
        List<Map<String, Object>> rows = jdbc.queryForList(sessionSql() + " WHERE s.id = UUID_TO_BIN(?) FOR UPDATE",
                sessionId.toString());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên ca");
        }
        Map<String, Object> row = rows.get(0);
        if (!"OPEN".equals(row.get("status"))) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Chỉ kết thúc được ca đang mở (hiện tại " + row.get("status") + ")");
        }
        return row;
    }

    private static int plannedMinutesOf(List<Map<String, Object>> runs) {
        return runs.isEmpty() ? 0 : ((Number) runs.get(0).get("plannedMinutes")).intValue();
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String bin(UUID id) {
        return id == null ? null : id.toString();
    }

    private void audit(UUID id, String action, List<FieldChange> changes) {
        var principal = currentUser.principal();
        audit.write(new AuditLogWriter.Entry("pr", "ShiftSession", id.toString(), action, changes, null,
                principal.map(p -> p.userId()).orElse(null), null, principal.map(p -> p.deviceId()).orElse(null),
                null));
    }
}
