package vn.pxvb.alerting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.pxvb.common.domain.AuditableEntity;

/**
 * Rule cảnh báo cấu hình được (FR-CC-050). Thay đổi rule được audit tự động.
 * {@code recipientPolicy}: {@code {"roles":[...],"users":[uuid...]}};
 * {@code escalationPolicy}: {@code {"levels":[{"afterMinutes":15,"roles":[...]}]}}.
 */
@Entity
@Table(name = "sys_alert_rule")
public class AlertRule extends AuditableEntity {

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 30)
    private String module;

    @Column(nullable = false, length = 100)
    private String metric;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Alert.Severity severity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String conditionJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String recipientPolicy = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String escalationPolicy = "{}";

    @Column(nullable = false)
    private int dedupWindowMin = 30;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** Số lần đã cấu hình; alert lưu lại khi raise để biết rule nào đã sinh ra nó (L67). */
    @Column(nullable = false)
    private int version = 1;

    protected AlertRule() {
    }

    public AlertRule(String code, String module, String metric, Alert.Severity severity, String recipientPolicy,
                     String escalationPolicy, int dedupWindowMin) {
        this.code = code;
        this.module = module;
        this.metric = metric;
        this.severity = severity;
        this.recipientPolicy = recipientPolicy;
        this.escalationPolicy = escalationPolicy;
        this.dedupWindowMin = dedupWindowMin;
    }

    public void configure(Alert.Severity severity, String conditionJson, String recipientPolicy,
                          String escalationPolicy, Integer dedupWindowMin, Boolean active) {
        if (severity != null) {
            this.severity = severity;
        }
        if (conditionJson != null) {
            this.conditionJson = conditionJson;
        }
        if (recipientPolicy != null) {
            this.recipientPolicy = recipientPolicy;
        }
        if (escalationPolicy != null) {
            this.escalationPolicy = escalationPolicy;
        }
        if (dedupWindowMin != null) {
            this.dedupWindowMin = dedupWindowMin;
        }
        if (active != null) {
            this.active = active;
        }
        this.version++;
    }

    public String getCode() {
        return code;
    }

    public int getVersion() {
        return version;
    }

    public String getModule() {
        return module;
    }

    public String getMetric() {
        return metric;
    }

    public Alert.Severity getSeverity() {
        return severity;
    }

    public String getConditionJson() {
        return conditionJson;
    }

    public String getRecipientPolicy() {
        return recipientPolicy;
    }

    public String getEscalationPolicy() {
        return escalationPolicy;
    }

    public int getDedupWindowMin() {
        return dedupWindowMin;
    }

    public boolean isActive() {
        return active;
    }
}
