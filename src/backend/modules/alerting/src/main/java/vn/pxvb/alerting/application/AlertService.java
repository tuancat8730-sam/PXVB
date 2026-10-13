package vn.pxvb.alerting.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import vn.pxvb.identity.contract.IdentityDirectory;

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

    private final RuleRepository rules;
    private final AlertRepository alerts;
    private final HistoryRepository history;
    private final RecipientRepository recipients;
    private final IdentityDirectory directory;
    private final NotificationService notifications;
    private final RealtimePublisher realtime;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AlertService(RuleRepository rules, AlertRepository alerts, HistoryRepository history,
                        RecipientRepository recipients, IdentityDirectory directory,
                        NotificationService notifications, RealtimePublisher realtime, ObjectMapper mapper,
                        Clock clock) {
        this.rules = rules;
        this.alerts = alerts;
        this.history = history;
        this.recipients = recipients;
        this.directory = directory;
        this.notifications = notifications;
        this.realtime = realtime;
        this.mapper = mapper;
        this.clock = clock;
    }

    // ---------- Phát cảnh báo ----------

    @Override
    @Transactional
    public AlertRef raise(AlertCommand c) {
        AlertRule rule = rules.findByCode(c.ruleCode()).orElseThrow(() -> new BusinessException(
                ErrorCode.VALIDATION, "Rule cảnh báo không tồn tại: " + c.ruleCode()));
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
            Alert alert = existing.get();
            alert.recordRecurrence(now, c.message(), payloadJson);
            return new AlertRef(alert.getId(), false);
        }

        Alert.Severity severity = c.severity() == null ? rule.getSeverity() : Alert.Severity.valueOf(c.severity());
        Alert alert = alerts.save(new Alert(rule.getCode(), severity, rule.getModule(), c.subjectType(),
                c.subjectId(), c.machineId(), c.shiftSessionId(), c.title(), c.message(), payloadJson, dedupKey,
                now));
        history.save(new AlertHistory(alert.getId(), null, Alert.Status.OPEN.name(), null, now, null));

        Set<UUID> targets = resolve(parsePolicy(rule.getRecipientPolicy()));
        deliver(alert, targets, 0, now, c.title());
        return new AlertRef(alert.getId(), true);
    }

    // ---------- Vòng đời ----------

    @Transactional
    public Alert acknowledge(UUID id, UUID userId, String note) {
        return move(id, Alert.Status.ACK, userId, note, null);
    }

    @Transactional
    public Alert startWork(UUID id, UUID userId, String note) {
        return move(id, Alert.Status.IN_PROGRESS, userId, note, null);
    }

    @Transactional
    public Alert resolve(UUID id, UUID userId, String cause, String note) {
        if (cause == null || cause.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cần chọn nguyên nhân khi xử lý xong cảnh báo",
                    Map.of("cause", List.of("Vui lòng chọn nguyên nhân")));
        }
        return move(id, Alert.Status.RESOLVED, userId, note, cause.trim());
    }

    @Transactional
    public Alert close(UUID id, UUID userId, String note) {
        return move(id, Alert.Status.CLOSED, userId, note, null);
    }

    private Alert move(UUID id, Alert.Status target, UUID userId, String note, String cause) {
        Alert alert = alerts.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
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
        int count = 0;
        for (Alert alert : alerts.findByStatus(Alert.Status.OPEN)) {
            AlertRule rule = rules.findByCode(alert.getRuleCode()).orElse(null);
            if (rule == null) {
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

    private void deliver(Alert alert, Set<UUID> targets, int level, Instant now, String title) {
        if (targets.isEmpty()) {
            return;
        }
        recipients.saveAll(targets.stream().map(u -> new AlertRecipient(alert.getId(), u, level, now)).toList());
        notifications.notifyUsers(targets, "ALERT", title, alert.getMessage(), "/alerts/" + alert.getId(),
                alert.getId());
        realtime.pushToUsers(targets, "/queue/alerts", AlertPush.of(level == 0 ? "RAISED" : "ESCALATED", alert));
    }

    // ---------- Truy vấn ----------

    @Transactional(readOnly = true)
    public Page<Alert> search(String module, Alert.Severity severity, Alert.Status status, UUID machineId,
                              Pageable pageable) {
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
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return alerts.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public AlertDetail detail(UUID id) {
        Alert alert = alerts.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return new AlertDetail(alert, history.findByAlertIdOrderByAt(id), recipients.findByAlertId(id));
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
