package vn.pxvb.alerting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.pxvb.common.domain.BaseEntity;

/** Người nhận cảnh báo (kể cả người nhận do leo thang, {@code escalationLevel} > 0). */
@Entity
@Table(name = "sys_alert_recipient")
public class AlertRecipient extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false)
    private UUID alertId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private int escalationLevel;

    @Column(nullable = false)
    private Instant deliveredAt;

    private Instant readAt;

    protected AlertRecipient() {
    }

    public AlertRecipient(UUID alertId, UUID userId, int escalationLevel, Instant deliveredAt) {
        this.alertId = alertId;
        this.userId = userId;
        this.escalationLevel = escalationLevel;
        this.deliveredAt = deliveredAt;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getEscalationLevel() {
        return escalationLevel;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
