package vn.pxvb.alerting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.pxvb.common.domain.BaseEntity;

/** Thông báo trong ứng dụng cho một người dùng (sys_notification). */
@Entity
@Table(name = "sys_notification")
public class Notification extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 2000)
    private String body;

    @Column(length = 300)
    private String deepLink;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID alertId;

    @Column(nullable = false, length = 20)
    private String channel = "INAPP";

    @Column(nullable = false)
    private Instant createdAt;

    private Instant readAt;

    protected Notification() {
    }

    public Notification(UUID userId, String type, String title, String body, String deepLink, UUID alertId,
                        Instant createdAt) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.deepLink = deepLink;
        this.alertId = alertId;
        this.createdAt = createdAt;
    }

    public void markRead(Instant at) {
        if (readAt == null) {
            readAt = at;
        }
    }

    public UUID getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getDeepLink() {
        return deepLink;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public String getChannel() {
        return channel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
