package vn.pxvb.alerting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.pxvb.common.domain.BaseEntity;

/** Lịch sử trạng thái xử lý cảnh báo (FR-CC-051). */
@Entity
@Table(name = "sys_alert_history")
public class AlertHistory extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false)
    private UUID alertId;

    @Column(length = 30)
    private String fromStatus;

    @Column(nullable = false, length = 30)
    private String toStatus;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID byUserId;

    @Column(nullable = false)
    private Instant at;

    @Column(length = 1000)
    private String note;

    protected AlertHistory() {
    }

    public AlertHistory(UUID alertId, String fromStatus, String toStatus, UUID byUserId, Instant at, String note) {
        this.alertId = alertId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.byUserId = byUserId;
        this.at = at;
        this.note = note;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public UUID getByUserId() {
        return byUserId;
    }

    public Instant getAt() {
        return at;
    }

    public String getNote() {
        return note;
    }
}
