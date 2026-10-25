package vn.pxvb.alerting.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.alerting.application.Repositories.AlertRepository;
import vn.pxvb.alerting.application.Repositories.HistoryRepository;
import vn.pxvb.alerting.application.Repositories.RecipientRepository;
import vn.pxvb.alerting.application.Repositories.RuleRepository;
import vn.pxvb.alerting.contract.AlertRaiser;
import vn.pxvb.alerting.domain.Alert;
import vn.pxvb.alerting.domain.AlertHistory;
import vn.pxvb.alerting.domain.AlertRecipient;
import vn.pxvb.alerting.domain.AlertRule;
import vn.pxvb.alerting.infrastructure.RealtimePublisher;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.common.security.DataScope;
import vn.pxvb.dataio.application.ExportService;
import vn.pxvb.identity.contract.IdentityDirectory;
import vn.pxvb.masterdata.contract.MasterdataQueries;

/**
 * Động cơ cảnh báo (FR-CC-050..053): khử trùng theo {@code dedupKey} trong {@code dedupWindowMin}, chọn người nhận
 * theo rule, vòng đời OPEN→ACK→IN_PROGRESS→RESOLVED→CLOSED có lịch sử, leo thang khi chưa ACK.
 */
@Service
public class AlertService implements AlertRaiser {

    public record AlertPush(String event, UUID alertId, String ruleCode, String severity, String status,
                            String module, String title, String message, int occurrenceCount, UUID machineId,
                            int escalationLevel) {
        static AlertPush of(String event, Alert a) {
            return new AlertPush(event, a.getId(), a.getRuleCode(), a.getSeverity().name(), a.getStatus().name(),
                    a.getModule(), a.getTitle(), a.getMessage(), a.getOccurrenceCount(), a.getMachineId(),
                    a.getEscalationLevel());
        }
    }

    public record AlertDetail(Alert alert, List<AlertHistory> history, List<AlertRecipient> recipients) {
    }

    record Policy(List<String> roles, List<UUID> users) {
    }

    record Level(int afterMinutes, List<String> roles) {
    }

    record Escalation(List<Level> levels) {
    }

    private static final Set<Alert.Status> ACTIVE = Set.of(Alert.Status.OPEN, Alert.Status.ACK,
            Alert.Status.IN_PROGRESS);
    private static final List<DataScope> ALL_SCOPES = List.of(DataScope.all());

    static final int MAX_EXPORT_ROWS = 50_000;

    /** Mức do module nghiệp vụ yêu cầu; {@code null} nghĩa là lấy theo rule. Giá trị sai bị từ chối (F7). */
    static Alert.Severity parseSeverity(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Alert.Severity.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION, "Mức cảnh báo không hợp lệ: " + value,
                    Map.of("severity", List.of("Mức phải là INFO, WARN hoặc CRITICAL")));
        }
    }

    /** Ghi chú xử lý bắt buộc khi RESOLVED: cắt khoảng trắng, không được rỗng (FR-CC-051, L64). */
    static String requireResolutionNote(String note) {
        if (note == null || note.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cần ghi chú khi xử lý xong cảnh báo",
                    Map.of("note", List.of("Vui lòng nhập ghi chú xử lý")));
        }
        return note.trim();
    }

    /** Từ chối xuất khi vượt trần, thay vì cắt im lặng (F6). */
    static void requireWithinExportLimit(long totalRows) {
        if (totalRows > MAX_EXPORT_ROWS) {
            throw new BusinessException(ErrorCode.VALIDATION, "Có " + totalRows + " dòng, vượt giới hạn "
                    + String.format("%,d", MAX_EXPORT_ROWS).replace(',', '.') + " dòng. Hãy thu hẹp bộ lọc.");
        }
    }

    private final RuleRepository rules;
    private final AlertRepository alerts;
    private final HistoryRepository history;
    private final RecipientRepository recipients;
    private final IdentityDirectory directory;
    private final NotificationService notifications;
    private final RealtimePublisher realtime;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final MasterdataQueries masterdata;
    private final EmailService email;
    private final ExportService exports;

    public AlertService(RuleRepository rules, AlertRepository alerts, HistoryRepository history,
                        RecipientRepository recipients, IdentityDirectory directory,
                        NotificationService notifications, RealtimePublisher realtime, ObjectMapper mapper,
                        Clock clock, MasterdataQueries masterdata, EmailService email, ExportService exports) {
        this.rules = rules;
        this.alerts = alerts;
        this.history = history;
        this.recipients = recipients;
        this.directory = directory;
        this.notifications = notifications;
        this.realtime = realtime;
        this.mapper = mapper;
        this.clock = clock;
        this.masterdata = masterdata;
        this.email = email;
        this.exports = exports;
    }

    // ---------- Phát cảnh báo ----------

    @Override
    @Transactional
    public AlertRef raise(AlertCommand c) {
        AlertRule rule = rules.findByCode(c.ruleCode()).orElseThrow(() -> new BusinessException(
                ErrorCode.VALIDATION, "Rule cảnh báo không tồn tại: " + c.ruleCode()));
        Alert.Severity requestedSeverity = parseSeverity(c.severity());
        if (!rule.isActive()) {
            return new AlertRef(null, false);
        }
        Instant now = clock.instant();
        String dedupKey = c.dedupKey() != null ? c.dedupKey()
                : c.ruleCode() + ":" + c.subjectType() + ":" + c.subjectId();
        Instant windowStart = now.minus(Duration.ofMinutes(rule.getDedupWindowMin()));
        var existing = alerts.findFirstByDedupKeyAndStatusInOrderByRaisedAtDesc(dedupKey, ACTIVE)
                .filter(a -> !a.getLastOccurredAt().isBefore(windowStart));
        String payloadJson = toJson(c.payload());
        if (existing.isPresent()) {
            return recur(existing.get(), rule, c, requestedSeverity, now, payloadJson);
        }

        Alert.Severity severity = requestedSeverity == null ? rule.getSeverity() : requestedSeverity;
        Alert alert = alerts.save(new Alert(rule.getCode(), severity, rule.getModule(), c.subjectType(),
                c.subjectId(), c.machineId(), lineCodeOf(c.machineId()), c.shiftSessionId(), c.title(), c.message(),
                payloadJson, dedupKey, now));
        history.save(new AlertHistory(alert.getId(), null, Alert.Status.OPEN.name(), null, now, null));

        Set<UUID> targets = resolve(parsePolicy(rule.getRecipientPolicy()));
        deliver(alert, targets, 0, now, c.title());
        return new AlertRef(alert.getId(), true);
    }

    // ---------- Vòng đời ----------

    @Transactional
    public Alert acknowledge(UUID id, UUID userId, String note, List<DataScope> scopes) {
        return move(id, Alert.Status.ACK, userId, note, null, scopes);
    }

    @Transactional
    public Alert startWork(UUID id, UUID userId, String note, List<DataScope> scopes) {
        return move(id, Alert.Status.IN_PROGRESS, userId, note, null, scopes);
    }

    @Transactional
    public Alert resolve(UUID id, UUID userId, String cause, String note, List<DataScope> scopes) {
        if (cause == null || cause.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cần chọn nguyên nhân khi xử lý xong cảnh báo",
                    Map.of("cause", List.of("Vui lòng chọn nguyên nhân")));
        }
        if (!masterdata.reasonExists("ALERT_CAUSE", cause.trim())) {
            throw new BusinessException(ErrorCode.VALIDATION, "Nguyên nhân không có trong danh mục",
                    Map.of("cause", List.of("Mã nguyên nhân '" + cause.trim()
                            + "' không thuộc danh mục ALERT_CAUSE")));
        }
        return move(id, Alert.Status.RESOLVED, userId, requireResolutionNote(note), cause.trim(), scopes);
    }

    @Transactional
    public Alert close(UUID id, UUID userId, String note, List<DataScope> scopes) {
        return move(id, Alert.Status.CLOSED, userId, note, null, scopes);
    }

    private Alert move(UUID id, Alert.Status target, UUID userId, String note, String cause,
                       List<DataScope> scopes) {
        Alert alert = alerts.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        requireInScope(alert, scopes);
        Alert.Status from = alert.getStatus();
        Instant now = clock.instant();
        alert.transitionTo(target, userId, now);
        if (target == Alert.Status.RESOLVED) {
            alert.resolutionDetails(cause, note);
        }
        history.save(new AlertHistory(id, from.name(), target.name(), userId, now, note));
        List<UUID> users = recipients.findByAlertId(id).stream().map(AlertRecipient::getUserId).distinct().toList();
        realtime.pushToUsers(users, "/queue/alerts", AlertPush.of("UPDATED", alert));
        return alert;
    }

    // ---------- Leo thang ----------

    /** Leo thang các cảnh báo chưa ACK quá hạn theo {@code escalationPolicy} (FR-CC-052). @return số cảnh báo leo thang. */
    @Transactional
    public int escalateDue(Instant now) {
        Map<String, AlertRule> rulesByCode = rules.findAll().stream()
                .collect(Collectors.toMap(AlertRule::getCode, Function.identity()));
        int count = 0;
        for (Alert alert : alerts.findByStatus(Alert.Status.OPEN)) {
            AlertRule rule = rulesByCode.get(alert.getRuleCode());
            if (rule == null || !rule.isActive()) {
                continue;
            }
            List<Level> levels = parseEscalation(rule.getEscalationPolicy());
            int index = alert.getEscalationLevel();
            if (index >= levels.size()) {
                continue;
            }
            Level level = levels.get(index);
            if (now.isBefore(alert.getRaisedAt().plus(Duration.ofMinutes(level.afterMinutes())))) {
                continue;
            }
            Set<UUID> already = new LinkedHashSet<>();
            recipients.findByAlertId(alert.getId()).forEach(r -> already.add(r.getUserId()));
            Set<UUID> targets = resolve(new Policy(level.roles(), List.of()));
            targets.removeAll(already);
            alert.escalate();
            history.save(new AlertHistory(alert.getId(), Alert.Status.OPEN.name(), Alert.Status.OPEN.name(), null,
                    now, "Leo thang cấp " + alert.getEscalationLevel()));
            deliver(alert, targets, alert.getEscalationLevel(), now, "[Leo thang] " + alert.getTitle());
            count++;
        }
        return count;
    }

    /**
     * Tái diễn trong cửa sổ dedup (L66): luôn tăng bộ đếm. Nếu mức yêu cầu cao hơn thì nâng mức, ghi lịch sử và gửi
     * lại cho người nhận hiện tại (kèm email nếu lên CRITICAL). Cùng mức hoặc thấp hơn thì không gửi lại.
     */
    private AlertRef recur(Alert alert, AlertRule rule, AlertCommand c, Alert.Severity requested, Instant now,
                           String payloadJson) {
        alert.recordRecurrence(now, c.message(), payloadJson);
        if (requested != null && alert.raiseSeverity(requested)) {
            history.save(new AlertHistory(alert.getId(), alert.getStatus().name(), alert.getStatus().name(), null,
                    now, "Nâng mức lên " + requested.name()));
            Set<UUID> targets = resolve(parsePolicy(rule.getRecipientPolicy()));
            renotify(alert, targets, "UPDATED", c.title());
        }
        return new AlertRef(alert.getId(), false);
    }

    private void deliver(Alert alert, Set<UUID> targets, int level, Instant now, String title) {
        if (targets.isEmpty()) {
            return;
        }
        recipients.saveAll(targets.stream().map(u -> new AlertRecipient(alert.getId(), u, level, now)).toList());
        renotify(alert, targets, level == 0 ? "RAISED" : "ESCALATED", title);
    }

    /** Gửi thông báo trong ứng dụng, push realtime, và email nếu cảnh báo đang ở mức CRITICAL. */
    private void renotify(Alert alert, Set<UUID> targets, String event, String title) {
        if (targets.isEmpty()) {
            return;
        }
        notifications.notifyUsers(targets, "ALERT", title, alert.getMessage(), "/alerts/" + alert.getId(),
                alert.getId());
        realtime.pushToUsers(targets, "/queue/alerts", AlertPush.of(event, alert));
        if (alert.getSeverity() == Alert.Severity.CRITICAL) {
            email.sendAfterCommit(targets, "[PXVB][CRITICAL] " + title,
                    alert.getMessage() == null ? alert.getTitle() : alert.getMessage(), "/alerts/" + alert.getId());
        }
    }

    /** Xuất danh sách cảnh báo ra Excel theo bộ lọc (FR-CC-054), tối đa 50.000 dòng. */
    @Transactional(readOnly = true)
    public byte[] export(String module, Alert.Severity severity, Alert.Status status, UUID machineId,
                         UUID shiftSessionId, List<DataScope> scopes) {
        Page<Alert> page = search(module, severity, status, machineId, shiftSessionId, scopes,
                org.springframework.data.domain.PageRequest.of(0, MAX_EXPORT_ROWS + 1,
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,
                                "raisedAt")));
        requireWithinExportLimit(page.getTotalElements());
        List<List<Object>> rows = page.getContent().stream().map(a -> List.<Object>of(
                a.getRaisedAt(), a.getRuleCode(), a.getSeverity().name(), a.getModule(),
                a.getTitle(), a.getStatus().name(), a.getOccurrenceCount(), a.getEscalationLevel(),
                a.getCause() == null ? "" : a.getCause(),
                a.getResolvedAt() == null ? "" : a.getResolvedAt())).toList();
        return exports.workbook("Cảnh báo", List.of("Thời điểm", "Mã rule", "Mức", "Phân hệ", "Tiêu đề",
                "Trạng thái", "Số lần", "Cấp leo thang", "Nguyên nhân", "Xử lý lúc"), rows);
    }

    // ---------- Truy vấn ----------

    @Transactional(readOnly = true)
    public Page<Alert> search(String module, Alert.Severity severity, Alert.Status status, UUID machineId,
                              UUID shiftSessionId, List<DataScope> scopes, Pageable pageable) {
        Specification<Alert> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (module != null && !module.isBlank()) {
                predicates.add(cb.equal(root.get("module"), module));
            }
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (machineId != null) {
                predicates.add(cb.equal(root.get("machineId"), machineId));
            }
            if (shiftSessionId != null) {
                predicates.add(cb.equal(root.get("shiftSessionId"), shiftSessionId));
            }
            predicates.add(lineScope(root, cb, scopes));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return alerts.findAll(spec, pageable);
    }

    /** Chi tiết cảnh báo cho hệ thống (không lọc phạm vi). Người dùng thật phải dùng bản có {@code scopes}. */
    @Transactional(readOnly = true)
    public AlertDetail detail(UUID id) {
        return detail(id, ALL_SCOPES);
    }

    @Transactional(readOnly = true)
    public AlertDetail detail(UUID id, List<DataScope> scopes) {
        Alert alert = alerts.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        requireInScope(alert, scopes);
        return new AlertDetail(alert, history.findByAlertIdOrderByAt(id), recipients.findByAlertId(id));
    }

    /** Phạm vi ALL thấy mọi cảnh báo; LINE chỉ thấy cảnh báo có dây chuyền khớp; UNIT chưa có dữ liệu nên không khớp. */
    private static Predicate lineScope(Root<Alert> root, CriteriaBuilder cb, List<DataScope> scopes) {
        if (grantsAll(scopes)) {
            return cb.conjunction();
        }
        List<String> lineCodes = scopes.stream()
                .filter(scope -> scope.type() == DataScope.Type.LINE && scope.value() != null)
                .map(DataScope::value)
                .toList();
        return lineCodes.isEmpty() ? cb.disjunction() : root.get("lineCode").in(lineCodes);
    }

    /** Ném 403 khi cảnh báo nằm ngoài phạm vi người dùng (FR-CC-006; quyết định L07). */
    private static void requireInScope(Alert alert, List<DataScope> scopes) {
        if (!DataScope.permits(scopes, DataScope.Type.LINE, alert.getLineCode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private static boolean grantsAll(List<DataScope> scopes) {
        return scopes.stream().anyMatch(scope -> scope.type() == DataScope.Type.ALL);
    }

    /** Dây chuyền của máy tại thời điểm raise; null nếu không có máy hoặc máy chưa gán dây chuyền. */
    private String lineCodeOf(UUID machineId) {
        if (machineId == null) {
            return null;
        }
        MasterdataQueries.MachineInfo machine = masterdata.machines(Set.of(machineId)).get(machineId);
        return machine == null ? null : machine.lineCode();
    }

    // ---------- Chính sách ----------

    private Set<UUID> resolve(Policy policy) {
        Set<UUID> users = new LinkedHashSet<>();
        if (policy.roles() != null) {
            policy.roles().forEach(r -> users.addAll(directory.userIdsWithRole(r)));
        }
        if (policy.users() != null) {
            users.addAll(policy.users());
        }
        return users;
    }

    private Policy parsePolicy(String json) {
        try {
            return mapper.readValue(json, Policy.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.VALIDATION, "Chính sách người nhận không hợp lệ");
        }
    }

    private List<Level> parseEscalation(String json) {
        try {
            Escalation escalation = mapper.readValue(json, Escalation.class);
            return escalation.levels() == null ? List.of() : escalation.levels();
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return mapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.VALIDATION, "Payload cảnh báo không hợp lệ");
        }
    }
}
