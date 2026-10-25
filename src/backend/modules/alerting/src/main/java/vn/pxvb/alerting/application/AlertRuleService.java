package vn.pxvb.alerting.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.alerting.application.Repositories.RuleRepository;
import vn.pxvb.alerting.domain.Alert;
import vn.pxvb.alerting.domain.AlertRule;
import vn.pxvb.common.audit.AuditLogWriter;
import vn.pxvb.common.audit.AuditLogWriter.FieldChange;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;

/** Sửa cấu hình rule cảnh báo: bắt buộc lý do và ghi audit trước/sau (FR-CC-050, L67). */
@Service
public class AlertRuleService {

    /** Thay đổi cấu hình; {@code null} nghĩa là giữ nguyên trường đó. */
    public record RuleChange(Alert.Severity severity, String conditionJson, String recipientPolicy,
                             String escalationPolicy, Integer dedupWindowMin, Boolean active, String reason) {
    }

    private static final String MODULE = "alerting";
    private static final String ENTITY_TYPE = "AlertRule";
    private static final String ACTION = "UPDATE";

    private final RuleRepository rules;
    private final AuditLogWriter audit;

    public AlertRuleService(RuleRepository rules, AuditLogWriter audit) {
        this.rules = rules;
        this.audit = audit;
    }

    @Transactional
    public AlertRule update(String code, RuleChange change, UUID userId, UUID deviceId) {
        requireReason(change.reason());
        AlertRule rule = rules.findByCode(code).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Map<String, Object> before = valuesOf(rule);
        rule.configure(change.severity(), change.conditionJson(), change.recipientPolicy(),
                change.escalationPolicy(), change.dedupWindowMin(), change.active());
        List<FieldChange> changes = diff(before, valuesOf(rule));
        audit.write(new AuditLogWriter.Entry(MODULE, ENTITY_TYPE, code, ACTION, changes, change.reason().trim(),
                userId, null, deviceId, null));
        return rule;
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cần nhập lý do khi thay đổi rule cảnh báo",
                    Map.of("reason", List.of("Vui lòng nhập lý do thay đổi")));
        }
    }

    private static Map<String, Object> valuesOf(AlertRule rule) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("severity", rule.getSeverity().name());
        values.put("condition", rule.getConditionJson());
        values.put("recipientPolicy", rule.getRecipientPolicy());
        values.put("escalationPolicy", rule.getEscalationPolicy());
        values.put("dedupWindowMin", rule.getDedupWindowMin());
        values.put("active", rule.isActive());
        return values;
    }

    private static List<FieldChange> diff(Map<String, Object> before, Map<String, Object> after) {
        List<FieldChange> changes = new ArrayList<>();
        before.forEach((field, oldValue) -> {
            Object newValue = after.get(field);
            if (!Objects.equals(oldValue, newValue)) {
                changes.add(new FieldChange(field, oldValue, newValue));
            }
        });
        return changes;
    }
}
