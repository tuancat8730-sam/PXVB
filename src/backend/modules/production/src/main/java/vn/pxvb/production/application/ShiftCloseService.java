package vn.pxvb.production.application;

import static vn.pxvb.production.application.ProductionTime.ts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.audit.AuditLogWriter;
import vn.pxvb.common.audit.AuditLogWriter.FieldChange;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.common.outbox.OutboxPublisher;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.identity.contract.IdentityDirectory;
import vn.pxvb.masterdata.contract.MasterdataQueries;
import vn.pxvb.production.contract.ProductionEvents.ShiftClosed;
import vn.pxvb.production.contract.ShiftCloseCheck;
import vn.pxvb.production.contract.ShiftCloseParty;
import vn.pxvb.production.contract.ShiftCloseCheck.Item;
import vn.pxvb.production.contract.ShiftCloseCheck.Level;
import vn.pxvb.workflow.application.ApprovalService;
import vn.pxvb.workflow.application.SignatureService;
import vn.pxvb.workflow.contract.WorkflowEvents.ApprovalCompleted;
import vn.pxvb.workflow.domain.Signature;

/**
 * Chốt ca (CC-11, FR-CC-080..083): bộ kiểm tra → giải trình/duyệt giải trình → ký + luồng duyệt đa bên
 * {@code SHIFT_CLOSE} → {@code CLOSED}: khóa ghi nhận, OEE chính thức, báo cáo ca, sự kiện {@code ShiftClosed}.
 */
@Service
public class ShiftCloseService {

    private static final String ENTITY_TYPE = "ShiftSession";
    private static final Set<String> CLOSE_ROLES = Set.of("SHIFT_LEADER", "DEPUTY_DIRECTOR");

    private final JdbcTemplate jdbc;
    private final List<ShiftCloseCheck> checks;
    private final List<ShiftCloseParty> parties;
    private final MasterdataQueries masterdata;
    private final ApprovalService approvals;
    private final SignatureService signatures;
    private final IdentityDirectory directory;
    private final OeeService oee;
    private final ProductionQueryService queries;
    private final OutboxPublisher outbox;
    private final ApplicationEventPublisher events;
    private final AuditLogWriter audit;
    private final CurrentUser currentUser;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ShiftCloseService(JdbcTemplate jdbc, List<ShiftCloseCheck> checks, List<ShiftCloseParty> parties,
                             MasterdataQueries masterdata,
                             ApprovalService approvals, SignatureService signatures, IdentityDirectory directory,
                             OeeService oee, ProductionQueryService queries, OutboxPublisher outbox,
                             ApplicationEventPublisher events, AuditLogWriter audit, CurrentUser currentUser,
                             ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.checks = checks;
        this.parties = parties;
        this.masterdata = masterdata;
        this.approvals = approvals;
        this.signatures = signatures;
        this.directory = directory;
        this.oee = oee;
        this.queries = queries;
        this.outbox = outbox;
        this.events = events;
        this.audit = audit;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.clock = clock;
    }

    private record Session(UUID id, LocalDate date, String shift, String status, UUID approvalId) {
    }

    // ---------- Kiểm tra ----------

    @Transactional(readOnly = true)
    public Map<String, Object> checks(UUID sessionId) {
        Session s = load(sessionId);
        return evaluate(s);
    }

    private Map<String, Object> evaluate(Session s) {
        Map<String, Map<String, Object>> explanations = new HashMap<>();
        jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, check_code, subject_key, reason_code, note, status "
                + "FROM pr_close_explanation WHERE session_id = UUID_TO_BIN(?)", s.id().toString())
                .forEach(e -> explanations.put(e.get("check_code") + "|" + e.get("subject_key"), e));
        List<Item> items = new ArrayList<>();
        for (ShiftCloseCheck check : checks) {
            items.addAll(check.evaluate(s.id(), s.date(), s.shift()));
        }
        items.sort(Comparator.comparing((Item i) -> i.level().ordinal()).reversed()
                .thenComparing(Item::checkCode).thenComparing(Item::subjectLabel));
        List<Map<String, Object>> view = new ArrayList<>();
        int blocking = 0;
        int needExplain = 0;
        for (Item item : items) {
            Map<String, Object> e = explanations.get(item.checkCode() + "|" + item.subjectKey());
            String exStatus = e == null ? null : (String) e.get("status");
            boolean resolved = switch (item.level()) {
                case PASS, WARN -> true;
                case EXPLAIN -> "SUBMITTED".equals(exStatus) || "APPROVED".equals(exStatus);
                case BLOCK -> item.approvable() && "APPROVED".equals(exStatus);
            };
            if (!resolved) {
                if (item.level() == Level.BLOCK) {
                    blocking++;
                } else {
                    needExplain++;
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("checkCode", item.checkCode());
            row.put("subjectKey", item.subjectKey());
            row.put("subjectLabel", item.subjectLabel());
            row.put("level", item.level().name());
            row.put("message", item.message());
            row.put("approvable", item.approvable());
            row.put("resolved", resolved);
            row.put("explanationId", e == null ? null : e.get("id"));
            row.put("explanationStatus", exStatus);
            row.put("explanationReason", e == null ? null : e.get("reason_code"));
            view.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", s.id().toString());
        out.put("status", s.status());
        out.put("closable", "CLOSING".equals(s.status()) && blocking == 0 && needExplain == 0);
        out.put("blocking", blocking);
        out.put("needExplanation", needExplain);
        out.put("items", view);
        return out;
    }

    // ---------- Giải trình ----------

    @Transactional
    public Map<String, Object> explain(UUID sessionId, String checkCode, String subjectKey, String reasonCode,
                                       String note) {
        Session s = load(sessionId);
        if (!"CLOSING".equals(s.status()) || s.approvalId() != null) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Chỉ nhập giải trình khi ca đang chờ chốt và chưa gửi duyệt");
        }
        String subject = subjectKey == null ? "" : subjectKey;
        Item item = findItem(s, checkCode, subject);
        if (item.level() == Level.PASS || item.level() == Level.WARN) {
            throw new BusinessException(ErrorCode.VALIDATION, "Mục kiểm tra này không cần giải trình");
        }
        if (item.level() == Level.BLOCK && !item.approvable()) {
            throw new BusinessException(ErrorCode.VALIDATION,
                    "Mục này chặn chốt ca và không thể giải trình: " + item.message());
        }
        validateReason(checkCode, reasonCode, note);
        UUID userId = currentUser.require().userId();
        String status = "SUBMITTED"; // mục BLOCK còn cần Phó QĐ duyệt (decideExplanation) mới gỡ được chặn
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.update("INSERT INTO pr_close_explanation(id, row_version, session_id, check_code, subject_key, "
                + "reason_code, note, status, created_at, created_by) VALUES (UUID_TO_BIN(?), 0, UUID_TO_BIN(?), "
                + "?, ?, ?, ?, ?, ?, UUID_TO_BIN(?)) ON DUPLICATE KEY UPDATE reason_code = VALUES(reason_code), "
                + "note = VALUES(note), status = VALUES(status), created_at = VALUES(created_at), "
                + "created_by = VALUES(created_by), decided_at = NULL, decided_by = NULL, "
                + "row_version = row_version + 1", id.toString(), sessionId.toString(), checkCode, subject,
                reasonCode, note, status, ts(clock.instant()), userId.toString());
        audit(sessionId, "EXPLAIN", List.of(new FieldChange("check", null, checkCode + ":" + subject),
                new FieldChange("reason", null, String.valueOf(reasonCode))), note);
        return evaluate(load(sessionId));
    }

    /** Phó QĐ duyệt/từ chối giải trình cho mục BLOCK có thể giải trình (vd thiếu lượt ghi sản lượng, AC-PR-006). */
    @Transactional
    public Map<String, Object> decideExplanation(UUID sessionId, UUID explanationId, boolean approve) {
        Session s = load(sessionId);
        if (!"CLOSING".equals(s.status()) || s.approvalId() != null) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Chỉ duyệt giải trình khi ca đang chờ chốt và chưa gửi duyệt");
        }
        UUID userId = currentUser.require().userId();
        int updated = jdbc.update("UPDATE pr_close_explanation SET status = ?, decided_at = ?, decided_by = "
                + "UUID_TO_BIN(?), row_version = row_version + 1 WHERE id = UUID_TO_BIN(?) AND session_id = "
                + "UUID_TO_BIN(?)", approve ? "APPROVED" : "REJECTED", ts(clock.instant()), userId.toString(),
                explanationId.toString(), sessionId.toString());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy giải trình");
        }
        audit(sessionId, approve ? "APPROVE_EXPLANATION" : "REJECT_EXPLANATION", List.of(
                new FieldChange("explanationId", null, explanationId.toString())), null);
        return evaluate(load(sessionId));
    }

    private void validateReason(String checkCode, String reasonCode, String note) {
        boolean hasNote = note != null && note.trim().length() >= 3;
        if ("CHK_OUTPUT_PLAN".equals(checkCode)) {
            if (reasonCode == null || !masterdata.reasonExists("PLAN_VARIANCE", reasonCode)) {
                throw new BusinessException(ErrorCode.VALIDATION, "Cần chọn nguyên nhân thiếu/vượt kế hoạch từ danh mục",
                        Map.of("reasonCode", List.of("Nguyên nhân không hợp lệ")));
            }
        } else if (!hasNote) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cần nhập nội dung giải trình",
                    Map.of("note", List.of("Vui lòng nhập giải trình (tối thiểu 3 ký tự)")));
        }
    }

    private Item findItem(Session s, String checkCode, String subject) {
        for (ShiftCloseCheck check : checks) {
            if (!check.code().equals(checkCode)) {
                continue;
            }
            for (Item item : check.evaluate(s.id(), s.date(), s.shift())) {
                if (item.subjectKey().equals(subject)) {
                    return item;
                }
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "Không có mục kiểm tra " + checkCode + " cho đối tượng này");
    }

    // ---------- Gửi chốt ----------

    @Transactional
    public Map<String, Object> requestClose(UUID sessionId, Signature.Method method, String secret, String note) {
        Session s = load(sessionId);
        if (!"CLOSING".equals(s.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Chỉ gửi chốt ca khi ca đã kết thúc (hiện tại " + s.status() + ")");
        }
        if (s.approvalId() != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "Ca đã gửi duyệt chốt");
        }
        Map<String, Object> result = evaluate(s);
        if (!Boolean.TRUE.equals(result.get("closable"))) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Chưa thể chốt ca: còn "
                    + result.get("blocking") + " mục chặn và " + result.get("needExplanation")
                    + " mục cần giải trình", Map.of("checks", unresolved(result)));
        }
        UUID userId = currentUser.require().userId();
        String role = directory.roleCodesOf(userId).stream().filter(CLOSE_ROLES::contains).min(String::compareTo)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN,
                        "Chỉ Trưởng ca/Phó Quản đốc được gửi chốt ca"));
        signatures.sign(ENTITY_TYPE, sessionId, Signature.Purpose.CONFIRM, role, userId,
                currentUser.deviceId().orElse(null), method, secret, SignatureService.hashOf(canonical(sessionId)));
        // Bên "không phát sinh" (theo dữ liệu ca) tự bỏ qua; có ghi audit
        List<ShiftCloseParty> skipped = parties.stream()
                .filter(p -> !p.hasActivity(sessionId, s.date(), s.shift())).toList();
        java.util.Set<String> skippedRoles = skipped.stream().map(ShiftCloseParty::roleCode)
                .collect(java.util.stream.Collectors.toSet());
        UUID instanceId = approvals.start("SHIFT_CLOSE", sessionId, userId, note, skippedRoles).instance().getId();
        jdbc.update("UPDATE pr_shift_session SET approval_instance_id = UUID_TO_BIN(?), row_version = "
                + "row_version + 1 WHERE id = UUID_TO_BIN(?)", instanceId.toString(), sessionId.toString());
        List<FieldChange> changes = new ArrayList<>();
        changes.add(new FieldChange("approvalInstanceId", null, instanceId.toString()));
        skipped.forEach(p -> changes.add(new FieldChange("skippedParty", null, p.label() + " (không phát sinh)")));
        audit(sessionId, "REQUEST_CLOSE", changes, note);
        Map<String, Object> out = new LinkedHashMap<>(result);
        out.put("approvalInstanceId", instanceId.toString());
        out.put("skippedParties", skipped.stream().map(ShiftCloseParty::label).toList());
        return out;
    }

    private static List<String> unresolved(Map<String, Object> result) {
        List<String> out = new ArrayList<>();
        for (Object o : (List<?>) result.get("items")) {
            Map<?, ?> m = (Map<?, ?>) o;
            if (Boolean.FALSE.equals(m.get("resolved"))) {
                out.add(m.get("level") + " " + m.get("checkCode") + ": " + m.get("message"));
            }
        }
        return out;
    }

    private String canonical(UUID sessionId) {
        Map<String, Object> totals = jdbc.queryForMap("SELECT COALESCE(SUM(output_qty), 0) AS qty, "
                + "COALESCE(SUM(output_count), 0) AS cnt, COALESCE(SUM(unplanned_stop_minutes + "
                + "planned_stop_minutes), 0) AS stops FROM pr_machine_run WHERE session_id = UUID_TO_BIN(?)",
                sessionId.toString());
        return sessionId + "|" + totals.get("qty") + "|" + totals.get("cnt") + "|" + totals.get("stops");
    }

    // ---------- Hoàn tất chốt ----------

    /** Luồng SHIFT_CLOSE xong: APPROVED → CLOSED; ngược lại giữ CLOSING để sửa/giải trình lại rồi gửi lại. */
    @EventListener
    @Transactional
    public void onApprovalCompleted(ApprovalCompleted event) {
        if (!ENTITY_TYPE.equals(event.entityType())) {
            return;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT business_date, shift_code, status FROM "
                + "pr_shift_session WHERE id = UUID_TO_BIN(?)", event.entityId().toString());
        if (rows.isEmpty() || !"CLOSING".equals(rows.get(0).get("status"))) {
            return;
        }
        UUID sessionId = event.entityId();
        if (!"APPROVED".equals(event.status())) {
            jdbc.update("UPDATE pr_shift_session SET approval_instance_id = NULL, row_version = row_version + 1 "
                    + "WHERE id = UUID_TO_BIN(?)", sessionId.toString());
            audit(sessionId, "CLOSE_" + event.status(), List.of(), null);
            return;
        }
        Instant now = clock.instant();
        LocalDate date = ((java.sql.Date) rows.get(0).get("business_date")).toLocalDate();
        String shift = (String) rows.get(0).get("shift_code");
        jdbc.update("UPDATE pr_shift_session SET status = 'CLOSED', closed_at = ?, row_version = row_version + 1, "
                + "updated_at = ? WHERE id = UUID_TO_BIN(?)", ts(now), ts(now), sessionId.toString());
        oee.computeSession(sessionId); // CLOSED → chính thức (trừ khi còn dừng kỹ thuật chưa xác nhận)
        int revision = snapshotReport(sessionId, date, shift, event.startedBy());
        audit(sessionId, "CLOSE", List.of(new FieldChange("status", "CLOSING", "CLOSED"),
                new FieldChange("reportRevision", null, String.valueOf(revision))), null);
        events.publishEvent(new ShiftClosed(sessionId, date, shift, now));
        outbox.publish("pr.shift.closed", Map.of("sessionId", sessionId.toString(), "businessDate", date.toString(),
                "shiftCode", shift, "reportRevision", revision));
    }

    // ---------- Báo cáo ca (ReportSnapshot, FR-PR-012) ----------

    private int snapshotReport(UUID sessionId, LocalDate date, String shift, UUID by) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId.toString());
        payload.put("businessDate", date);
        payload.put("shiftCode", shift);
        payload.put("generatedAt", clock.instant());
        payload.put("dashboard", queries.dashboard(sessionId));
        payload.put("oee", oee.sessionOee(sessionId));
        payload.put("outputByProduct", jdbc.queryForList("SELECT BIN_TO_UUID(e.product_id) AS productId, e.unit, "
                + "SUM(e.qty) AS qty FROM pr_output_entry e JOIN pr_machine_run r ON r.id = e.run_id WHERE "
                + "r.session_id = UUID_TO_BIN(?) GROUP BY e.product_id, e.unit", sessionId.toString()));
        payload.put("stopsByReason", jdbc.queryForList("SELECT s.reason_code AS reasonCode, COUNT(*) AS events, "
                + "SUM(TIMESTAMPDIFF(SECOND, s.start_at, s.end_at)) DIV 60 AS minutes FROM pr_stop_event s JOIN "
                + "pr_machine_run r ON r.id = s.run_id WHERE r.session_id = UUID_TO_BIN(?) AND s.end_at IS NOT NULL "
                + "GROUP BY s.reason_code ORDER BY minutes DESC", sessionId.toString()));
        payload.put("closeChecks", evaluate(new Session(sessionId, date, shift, "CLOSING", null)).get("items"));
        Integer revision = jdbc.queryForObject("SELECT COALESCE(MAX(revision), 0) + 1 FROM pr_shift_report "
                + "WHERE session_id = UUID_TO_BIN(?)", Integer.class, sessionId.toString());
        try {
            jdbc.update("INSERT INTO pr_shift_report(id, session_id, revision, payload, created_at, created_by) "
                    + "VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, UUID_TO_BIN(?))",
                    UuidCreator.getTimeOrderedEpoch().toString(), sessionId.toString(), revision,
                    mapper.writeValueAsString(payload), ts(clock.instant()), by == null ? null : by.toString());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return revision;
    }

    @Transactional(readOnly = true)
    public JsonNode report(UUID sessionId) {
        List<String> rows = jdbc.queryForList("SELECT payload FROM pr_shift_report WHERE session_id = "
                + "UUID_TO_BIN(?) ORDER BY revision DESC LIMIT 1", String.class, sessionId.toString());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Ca chưa được chốt nên chưa có báo cáo ca");
        }
        try {
            return mapper.readTree(rows.get(0));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- Nội bộ ----------

    private Session load(UUID id) {
        List<Session> rows = jdbc.query("SELECT business_date, shift_code, status, BIN_TO_UUID("
                + "approval_instance_id) AS approval FROM pr_shift_session WHERE id = UUID_TO_BIN(?)",
                (rs, i) -> new Session(id, rs.getDate("business_date").toLocalDate(), rs.getString("shift_code"),
                        rs.getString("status"), rs.getString("approval") == null ? null
                        : UUID.fromString(rs.getString("approval"))), id.toString());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên ca");
        }
        return rows.get(0);
    }

    private void audit(UUID id, String action, List<FieldChange> changes, String reason) {
        var principal = currentUser.principal();
        audit.write(new AuditLogWriter.Entry("pr", "ShiftSession", id.toString(), action, changes, reason,
                principal.map(p -> p.userId()).orElse(null), null, principal.map(p -> p.deviceId()).orElse(null),
                null));
    }
}
