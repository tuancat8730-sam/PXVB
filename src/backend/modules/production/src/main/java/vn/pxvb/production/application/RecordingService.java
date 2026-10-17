package vn.pxvb.production.application;

import static vn.pxvb.production.application.ProductionTime.instant;
import static vn.pxvb.production.application.ProductionTime.ts;

import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import vn.pxvb.masterdata.contract.MasterdataQueries.ProductInfo;
import vn.pxvb.masterdata.contract.MasterdataQueries.StopReasonInfo;
import vn.pxvb.production.contract.ProductionEvents.StopEnded;
import vn.pxvb.production.contract.ProductionEvents.StopStarted;

/**
 * Ghi nhận tại máy (FR-PR-002/003/004/005): sản lượng định kỳ, dừng máy, đổi mác. Mọi lệnh chỉ hợp lệ khi phiên
 * ca {@code OPEN} (BR-PR-001); thời gian dùng {@code OccurredAt} của tablet (BR-PR-007), không dùng giờ đồng bộ.
 */
@Service
public class RecordingService {

    public record Actor(UUID userId, UUID deviceId, UUID onBehalfOf) {
    }

    public record OutputInput(UUID entryId, UUID machineId, UUID sessionId, Long qty, Long counterReading,
                              Instant periodFrom, Instant periodTo, String note, Instant occurredAt) {
    }

    public record StopInput(UUID stopId, UUID machineId, UUID sessionId, String reasonCode, Instant startAt,
                            Instant endAt, String note) {
    }

    public record ChangeInput(UUID changeId, UUID machineId, UUID sessionId, UUID toProductId, Instant changedAt,
                              boolean startStopA) {
    }

    private static final int TOLERANCE_FUTURE_SECONDS = 300;

    private final JdbcTemplate jdbc;
    private final MasterdataQueries masterdata;
    private final OutboxPublisher outbox;
    private final ApplicationEventPublisher events;
    private final AuditLogWriter audit;
    private final CurrentUser currentUser;
    private final Clock clock;

    public RecordingService(JdbcTemplate jdbc, MasterdataQueries masterdata, OutboxPublisher outbox,
                            ApplicationEventPublisher events, AuditLogWriter audit, CurrentUser currentUser,
                            Clock clock) {
        this.jdbc = jdbc;
        this.masterdata = masterdata;
        this.outbox = outbox;
        this.events = events;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    private record Run(UUID runId, UUID sessionId, UUID machineId, String status, String sessionStatus,
                       UUID currentProductId, int segmentNo, Long lastCounter, Instant actualStart,
                       Instant plannedEnd, java.time.LocalDate businessDate) {
    }

    // ---------- Sản lượng ----------

    @Transactional
    public Map<String, Object> recordOutput(OutputInput in, Actor actor) {
        if (in.entryId() != null) {
            List<Map<String, Object>> dup = jdbc.queryForList(entrySql() + " WHERE e.id = UUID_TO_BIN(?)",
                    in.entryId().toString());
            if (!dup.isEmpty()) {
                return dup.get(0); // lệnh tablet gửi lại: trả bản ghi đã có
            }
        }
        Run run = lockRun(in.machineId(), in.sessionId());
        requireOpen(run);
        requireOrder(run);
        Instant now = clock.instant();
        Instant occurredAt = in.occurredAt() == null ? now : in.occurredAt();
        Instant periodTo = in.periodTo() == null ? occurredAt : in.periodTo();
        List<Map<String, Object>> last = jdbc.queryForList("SELECT period_to AS periodTo FROM pr_output_entry "
                + "WHERE run_id = UUID_TO_BIN(?) ORDER BY period_to DESC LIMIT 1", run.runId().toString());
        Instant previousEnd = last.isEmpty() ? run.actualStart() : instant(last.get(0).get("periodTo"));
        Instant periodFrom = in.periodFrom() == null ? previousEnd : in.periodFrom();

        if (!periodTo.isAfter(periodFrom)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Thời điểm kết thúc khoảng phải sau thời điểm bắt đầu");
        }
        if (periodFrom.isBefore(previousEnd) && !last.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Khoảng ghi nhận trùng với lần ghi trước (đến "
                    + previousEnd + ")");
        }
        int interval = outputInterval(run.machineId(), run.businessDate());
        if (Duration.between(periodFrom, periodTo).toMinutes() > 2L * interval) {
            throw new BusinessException(ErrorCode.VALIDATION, "Khoảng ghi nhận vượt " + 2 * interval
                    + " phút; hãy tách thành nhiều lần ghi (có ghi chú nhập bù)");
        }
        long qty = resolveQty(in, run);
        MachineInfo machine = masterdata.machines(List.of(run.machineId())).get(run.machineId());
        String unit = machine != null && "MAKER".equals(machine.machineType()) ? "RODS" : "PACKS";

        UUID id = in.entryId() == null ? UuidCreator.getTimeOrderedEpoch() : in.entryId();
        jdbc.update("INSERT INTO pr_output_entry(id, row_version, created_at, created_by, run_id, machine_id, "
                        + "product_id, segment_no, period_from, period_to, qty, unit, counter_reading, note, "
                        + "entered_by, on_behalf_of, occurred_at, device_id) VALUES (UUID_TO_BIN(?), 0, ?, "
                        + "UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, ?, "
                        + "UUID_TO_BIN(?), UUID_TO_BIN(?), ?, UUID_TO_BIN(?))",
                id.toString(), ts(now), str(actor.userId()), run.runId().toString(), run.machineId().toString(),
                str(run.currentProductId()), run.segmentNo(), ts(periodFrom), ts(periodTo), qty, unit,
                in.counterReading(), in.note(), str(actor.userId()), str(actor.onBehalfOf()), ts(occurredAt),
                str(actor.deviceId()));
        jdbc.update("UPDATE pr_machine_run SET output_qty = output_qty + ?, output_count = output_count + 1, "
                + "last_counter_reading = COALESCE(?, last_counter_reading), status = IF(status = 'NOT_STARTED', "
                + "'RUNNING', status), row_version = row_version + 1 WHERE id = UUID_TO_BIN(?)", qty,
                in.counterReading(), run.runId().toString());
        audit("OutputEntry", id, "RECORD", List.of(new FieldChange("qty", null, String.valueOf(qty)),
                new FieldChange("machineId", null, run.machineId().toString())), actor);
        return jdbc.queryForList(entrySql() + " WHERE e.id = UUID_TO_BIN(?)", id.toString()).get(0);
    }

    private long resolveQty(OutputInput in, Run run) {
        Long qty = in.qty();
        if (qty == null) {
            if (in.counterReading() == null) {
                throw new BusinessException(ErrorCode.VALIDATION, "Cần nhập số lượng hoặc số đồng hồ lũy kế");
            }
            if (run.lastCounter() == null) {
                throw new BusinessException(ErrorCode.VALIDATION,
                        "Lần ghi đầu tiên chưa có số đồng hồ nền: hãy nhập số lượng khoảng (kèm số đồng hồ nếu có)");
            }
            qty = in.counterReading() - run.lastCounter();
        }
        if (qty < 0) {
            throw new BusinessException(ErrorCode.VALIDATION, "Sản lượng không được âm (số đồng hồ nhỏ hơn lần trước?)");
        }
        return qty;
    }

    // ---------- Dừng máy ----------

    @Transactional
    public Map<String, Object> startStop(StopInput in, Actor actor) {
        if (in.stopId() != null) {
            List<Map<String, Object>> dup = jdbc.queryForList(stopSql() + " WHERE s.id = UUID_TO_BIN(?)",
                    in.stopId().toString());
            if (!dup.isEmpty()) {
                return dup.get(0);
            }
        }
        Run run = lockRun(in.machineId(), in.sessionId());
        requireOpen(run);
        requireOrder(run);
        StopReasonInfo reason = masterdata.stopReason(in.reasonCode() == null ? "" : in.reasonCode().toUpperCase())
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION,
                        "Mã dừng máy " + in.reasonCode() + " không tồn tại hoặc đã ngừng sử dụng"));
        Instant now = clock.instant();
        Instant startAt = in.startAt() == null ? now : in.startAt();
        if (startAt.isBefore(run.actualStart()) || startAt.isAfter(run.plannedEnd())) {
            throw new BusinessException(ErrorCode.VALIDATION, "Thời điểm dừng nằm ngoài ca làm việc");
        }
        if (startAt.isAfter(now.plusSeconds(TOLERANCE_FUTURE_SECONDS))) {
            throw new BusinessException(ErrorCode.VALIDATION, "Thời điểm dừng không được ở tương lai");
        }
        Instant endAt = in.endAt();
        if (endAt != null && !endAt.isAfter(startAt)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Thời điểm chạy lại phải sau thời điểm dừng");
        }
        assertNoOverlap(run.machineId(), startAt, endAt, null);

        UUID id = in.stopId() == null ? UuidCreator.getTimeOrderedEpoch() : in.stopId();
        jdbc.update("INSERT INTO pr_stop_event(id, row_version, created_at, created_by, run_id, machine_id, "
                        + "reason_code, planned_downtime, start_at, end_at, note, entered_by, device_id) VALUES ("
                        + "UUID_TO_BIN(?), 0, ?, UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, ?, ?, "
                        + "UUID_TO_BIN(?), UUID_TO_BIN(?))",
                id.toString(), ts(now), str(actor.userId()), run.runId().toString(), run.machineId().toString(),
                reason.code(), reason.countsAsPlannedDowntime(), ts(startAt), ts(endAt), in.note(),
                str(actor.userId()), str(actor.deviceId()));
        if (endAt == null) {
            jdbc.update("UPDATE pr_machine_run SET status = 'STOPPED', row_version = row_version + 1 "
                    + "WHERE id = UUID_TO_BIN(?)", run.runId().toString());
        }
        refreshMinutes(run.runId(), now);
        events.publishEvent(new StopStarted(id, run.sessionId(), run.machineId(), reason.code(), startAt));
        outbox.publish("pr.stop.started", Map.of("stopId", id.toString(), "sessionId", run.sessionId().toString(),
                "machineId", run.machineId().toString(), "reasonCode", reason.code()));
        audit("StopEvent", id, "START", List.of(new FieldChange("reasonCode", null, reason.code()),
                new FieldChange("startAt", null, startAt.toString())), actor);
        Map<String, Object> result = new LinkedHashMap<>(jdbc.queryForList(stopSql() + " WHERE s.id = "
                + "UUID_TO_BIN(?)", id.toString()).get(0));
        result.put("canCreateRepairRequest", reason.canCreateRepairRequest());
        result.put("requiresTechConfirm", reason.requiresTechConfirm());
        return result;
    }

    @Transactional
    public Map<String, Object> endStop(UUID machineId, UUID sessionId, Instant endAt, Actor actor) {
        Run run = lockRun(machineId, sessionId);
        requireOpen(run);
        List<Map<String, Object>> open = jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, start_at AS startAt, "
                + "reason_code AS reasonCode FROM pr_stop_event WHERE machine_id = UUID_TO_BIN(?) AND "
                + "run_id = UUID_TO_BIN(?) AND end_at IS NULL", machineId.toString(), run.runId().toString());
        if (open.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Máy không có sự kiện dừng đang mở");
        }
        UUID stopId = UUID.fromString((String) open.get(0).get("id"));
        Instant startAt = instant(open.get(0).get("startAt"));
        Instant now = clock.instant();
        Instant end = endAt == null ? now : endAt;
        if (!end.isAfter(startAt)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Thời điểm chạy lại phải sau thời điểm dừng ("
                    + startAt + ")");
        }
        jdbc.update("UPDATE pr_stop_event SET end_at = ?, row_version = row_version + 1 WHERE id = UUID_TO_BIN(?)",
                ts(end), stopId.toString());
        jdbc.update("UPDATE pr_machine_run SET status = 'RUNNING', row_version = row_version + 1 "
                + "WHERE id = UUID_TO_BIN(?)", run.runId().toString());
        refreshMinutes(run.runId(), now);
        String reason = (String) open.get(0).get("reasonCode");
        events.publishEvent(new StopEnded(stopId, run.sessionId(), machineId, reason, startAt, end));
        outbox.publish("pr.stop.ended", Map.of("stopId", stopId.toString(), "sessionId", run.sessionId().toString(),
                "machineId", machineId.toString(), "reasonCode", reason,
                "durationMin", Duration.between(startAt, end).toMinutes()));
        audit("StopEvent", stopId, "END", List.of(new FieldChange("endAt", null, end.toString())), actor);
        return jdbc.queryForList(stopSql() + " WHERE s.id = UUID_TO_BIN(?)", stopId.toString()).get(0);
    }

    /** FR-PR-005: kỹ thuật xác nhận nguyên nhân dừng — điều kiện để OEE chính thức. */
    @Transactional
    public Map<String, Object> confirmTech(UUID stopId, String techCauseCode, Actor actor) {
        if (techCauseCode == null || techCauseCode.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cần nhập mã nguyên nhân kỹ thuật");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(stopSql() + " WHERE s.id = UUID_TO_BIN(?)",
                stopId.toString());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy sự kiện dừng máy");
        }
        String reason = (String) rows.get(0).get("reasonCode");
        StopReasonInfo info = masterdata.stopReason(reason).orElse(null);
        if (info == null || !info.requiresTechConfirm()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Mã dừng " + reason + " không cần xác nhận kỹ thuật");
        }
        jdbc.update("UPDATE pr_stop_event SET tech_cause_code = ?, tech_confirmed_by = UUID_TO_BIN(?), "
                + "tech_confirmed_at = UTC_TIMESTAMP(6), row_version = row_version + 1 WHERE id = UUID_TO_BIN(?)",
                techCauseCode.trim(), str(actor.userId()), stopId.toString());
        audit("StopEvent", stopId, "CONFIRM_TECH", List.of(new FieldChange("techCauseCode", null,
                techCauseCode.trim())), actor);
        return jdbc.queryForList(stopSql() + " WHERE s.id = UUID_TO_BIN(?)", stopId.toString()).get(0);
    }

    // ---------- Đổi mác ----------

    @Transactional
    public Map<String, Object> changeProduct(ChangeInput in, Actor actor) {
        if (in.changeId() != null) {
            List<Map<String, Object>> dup = jdbc.queryForList(changeSql() + " WHERE c.id = UUID_TO_BIN(?)",
                    in.changeId().toString());
            if (!dup.isEmpty()) {
                return dup.get(0);
            }
        }
        Run run = lockRun(in.machineId(), in.sessionId());
        requireOpen(run);
        requireOrder(run);
        if (in.toProductId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cần chọn sản phẩm mới");
        }
        ProductInfo product = masterdata.products(List.of(in.toProductId())).get(in.toProductId());
        if (product == null || !product.active()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Sản phẩm không tồn tại hoặc đã ngừng sử dụng");
        }
        if (in.toProductId().equals(run.currentProductId())) {
            throw new BusinessException(ErrorCode.VALIDATION, "Sản phẩm mới trùng với sản phẩm đang chạy");
        }
        var allowed = masterdata.allowedMachineIds(in.toProductId());
        if (!allowed.isEmpty() && !allowed.contains(run.machineId())) {
            throw new BusinessException(ErrorCode.VALIDATION,
                    "Sản phẩm " + product.code() + " không được phép chạy trên máy này");
        }
        Instant now = clock.instant();
        Instant changedAt = in.changedAt() == null ? now : in.changedAt();
        int segment = run.segmentNo() + 1;
        UUID id = in.changeId() == null ? UuidCreator.getTimeOrderedEpoch() : in.changeId();
        UUID stopId = null;
        if (in.startStopA()) {
            boolean hasOpen = Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*) > 0 FROM pr_stop_event "
                    + "WHERE machine_id = UUID_TO_BIN(?) AND end_at IS NULL", Boolean.class,
                    run.machineId().toString()));
            if (!hasOpen) {
                Map<String, Object> stop = startStop(new StopInput(null, run.machineId(), run.sessionId(), "A",
                        changedAt, null, "Đổi mác sang " + product.code()), actor);
                stopId = UUID.fromString((String) stop.get("id"));
            }
        }
        jdbc.update("INSERT INTO pr_product_change(id, created_at, created_by, run_id, machine_id, "
                        + "from_product_id, to_product_id, segment_no, changed_at, stop_event_id, entered_by) "
                        + "VALUES (UUID_TO_BIN(?), ?, UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), "
                        + "UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), UUID_TO_BIN(?))",
                id.toString(), ts(now), str(actor.userId()), run.runId().toString(), run.machineId().toString(),
                str(run.currentProductId()), in.toProductId().toString(), segment, ts(changedAt), str(stopId),
                str(actor.userId()));
        jdbc.update("UPDATE pr_machine_run SET current_product_id = UUID_TO_BIN(?), segment_no = ?, "
                + "row_version = row_version + 1 WHERE id = UUID_TO_BIN(?)", in.toProductId().toString(), segment,
                run.runId().toString());
        audit("ProductChange", id, "CHANGE", List.of(new FieldChange("productId", str(run.currentProductId()),
                in.toProductId().toString())), actor);
        return jdbc.queryForList(changeSql() + " WHERE c.id = UUID_TO_BIN(?)", id.toString()).get(0);
    }

    // ---------- Nội bộ ----------

    private void assertNoOverlap(UUID machineId, Instant startAt, Instant endAt, UUID excludeId) {
        // sự kiện hiện có [s, e) chồng với [startAt, endAt) khi s < endAt (hoặc endAt = ∞) và (e = ∞ hoặc e > startAt)
        StringBuilder sql = new StringBuilder("SELECT reason_code, start_at, end_at FROM pr_stop_event WHERE "
                + "machine_id = UUID_TO_BIN(?) AND (end_at IS NULL OR end_at > ?)");
        List<Object> args = new ArrayList<>(List.of(machineId.toString(), ts(startAt)));
        if (endAt != null) {
            sql.append(" AND start_at < ?");
            args.add(ts(endAt));
        }
        if (excludeId != null) {
            sql.append(" AND id <> UUID_TO_BIN(?)");
            args.add(excludeId.toString());
        }
        sql.append(" LIMIT 1");
        List<Map<String, Object>> clash = jdbc.queryForList(sql.toString(), args.toArray());
        if (!clash.isEmpty()) {
            Map<String, Object> c = clash.get(0);
            throw new BusinessException(ErrorCode.CONFLICT, "Sự kiện dừng bị chồng lấn thời gian với sự kiện mã "
                    + c.get("reason_code") + " (từ " + instant(c.get("start_at")) + " đến "
                    + (c.get("end_at") == null ? "chưa kết thúc" : instant(c.get("end_at"))) + ")");
        }
    }

    private void refreshMinutes(UUID runId, Instant asOf) {
        int[] stops = stopMinutes(jdbc, runId, asOf);
        jdbc.update("UPDATE pr_machine_run SET planned_stop_minutes = ?, unplanned_stop_minutes = ?, "
                + "run_minutes = GREATEST(0, planned_minutes - ? - ?) WHERE id = UUID_TO_BIN(?)", stops[0],
                stops[1], stops[0], stops[1], runId.toString());
    }

    /** @return {@code [phút dừng kế hoạch, phút dừng ngoài kế hoạch]}; sự kiện đang mở tính đến {@code asOf}. */
    static int[] stopMinutes(JdbcTemplate jdbc, UUID runId, Instant asOf) {
        long planned = 0;
        long unplanned = 0;
        for (Map<String, Object> e : jdbc.queryForList("SELECT planned_downtime, start_at, end_at FROM "
                + "pr_stop_event WHERE run_id = UUID_TO_BIN(?)", runId.toString())) {
            Instant start = instant(e.get("start_at"));
            Instant end = e.get("end_at") == null ? asOf : instant(e.get("end_at"));
            long seconds = Math.max(0, Duration.between(start, end).getSeconds());
            if (Boolean.TRUE.equals(e.get("planned_downtime"))) {
                planned += seconds;
            } else {
                unplanned += seconds;
            }
        }
        return new int[]{(int) (planned / 60), (int) (unplanned / 60)};
    }

    private int outputInterval(UUID machineId, java.time.LocalDate date) {
        return masterdata.resolveThreshold("OUTPUT_INTERVAL_MIN", machineId, null, null, null, null, date)
                .map(t -> t.value().intValue()).filter(v -> v > 0)
                .orElse(ShiftSessionService.DEFAULT_OUTPUT_INTERVAL_MIN);
    }

    private Run lockRun(UUID machineId, UUID sessionId) {
        if (machineId == null) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cần chọn máy");
        }
        String sql = "SELECT BIN_TO_UUID(r.id) AS runId, BIN_TO_UUID(r.session_id) AS sessionId, r.status, "
                + "s.status AS sessionStatus, BIN_TO_UUID(r.current_product_id) AS productId, r.segment_no, "
                + "r.last_counter_reading AS lastCounter, s.actual_start, s.planned_end, s.business_date "
                + "FROM pr_machine_run r JOIN pr_shift_session s ON s.id = r.session_id WHERE r.machine_id = "
                + "UUID_TO_BIN(?)";
        List<Object> args = new ArrayList<>(List.of(machineId.toString()));
        if (sessionId != null) {
            sql += " AND r.session_id = UUID_TO_BIN(?)";
            args.add(sessionId.toString());
        } else {
            sql += " AND s.status = 'OPEN'";
        }
        sql += " ORDER BY s.planned_start DESC LIMIT 1 FOR UPDATE";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Máy chưa có phiên ca đang mở để ghi nhận (hãy bắt đầu ca trước)");
        }
        Map<String, Object> r = rows.get(0);
        return new Run(UUID.fromString((String) r.get("runId")), UUID.fromString((String) r.get("sessionId")),
                machineId, (String) r.get("status"), (String) r.get("sessionStatus"),
                r.get("productId") == null ? null : UUID.fromString((String) r.get("productId")),
                ((Number) r.get("segment_no")).intValue(),
                r.get("lastCounter") == null ? null : ((Number) r.get("lastCounter")).longValue(),
                instant(r.get("actual_start")), instant(r.get("planned_end")),
                ((java.sql.Date) r.get("business_date")).toLocalDate());
    }

    private static void requireOpen(Run run) {
        if (!"OPEN".equals(run.sessionStatus())) {
            throw new BusinessException(ErrorCode.DATA_LOCKED, "Ca đã " + ("CLOSING".equals(run.sessionStatus())
                    ? "kết thúc" : "chốt") + ", chỉ có thể điều chỉnh qua yêu cầu điều chỉnh dữ liệu");
        }
    }

    private static void requireOrder(Run run) {
        if ("NO_ORDER".equals(run.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Máy không có lệnh sản xuất trong ca này");
        }
    }

    private static String str(UUID id) {
        return id == null ? null : id.toString();
    }

    private static String entrySql() {
        return "SELECT BIN_TO_UUID(e.id) AS id, BIN_TO_UUID(e.run_id) AS runId, BIN_TO_UUID(e.machine_id) AS "
                + "machineId, BIN_TO_UUID(e.product_id) AS productId, e.segment_no AS segmentNo, e.period_from AS "
                + "periodFrom, e.period_to AS periodTo, e.qty, e.unit, e.counter_reading AS counterReading, "
                + "e.note, BIN_TO_UUID(e.entered_by) AS enteredBy, BIN_TO_UUID(e.on_behalf_of) AS onBehalfOf, "
                + "e.occurred_at AS occurredAt FROM pr_output_entry e";
    }

    static String stopSql() {
        return "SELECT BIN_TO_UUID(s.id) AS id, BIN_TO_UUID(s.run_id) AS runId, BIN_TO_UUID(s.machine_id) AS "
                + "machineId, s.reason_code AS reasonCode, s.planned_downtime AS plannedDowntime, s.start_at AS "
                + "startAt, s.end_at AS endAt, s.auto_closed AS autoClosed, s.note, s.tech_cause_code AS "
                + "techCauseCode, BIN_TO_UUID(s.tech_confirmed_by) AS techConfirmedBy, s.tech_confirmed_at AS "
                + "techConfirmedAt FROM pr_stop_event s";
    }

    private static String changeSql() {
        return "SELECT BIN_TO_UUID(c.id) AS id, BIN_TO_UUID(c.run_id) AS runId, BIN_TO_UUID(c.machine_id) AS "
                + "machineId, BIN_TO_UUID(c.from_product_id) AS fromProductId, BIN_TO_UUID(c.to_product_id) AS "
                + "toProductId, c.segment_no AS segmentNo, c.changed_at AS changedAt, BIN_TO_UUID("
                + "c.stop_event_id) AS stopEventId FROM pr_product_change c";
    }

    private void audit(String entityType, UUID id, String action, List<FieldChange> changes, Actor actor) {
        audit.write(new AuditLogWriter.Entry("pr", entityType, id.toString(), action, changes, null,
                actor.userId(), null, actor.deviceId(), null));
    }
}
