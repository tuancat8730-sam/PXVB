package vn.pxvb.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.pxvb.common.domain.BaseEntity;

/**
 * Xác nhận điện tử nội bộ (không phải chữ ký số pháp lý — Q13): ghi người ký, vai trò, phương thức xác thực lại
 * và SHA-256 của bản ghi tại thời điểm ký. Bản ghi đổi sau đó → hash lệch → chữ ký vô hiệu (FR-CC-031).
 */
@Entity
@Table(name = "sys_signature")
public class Signature extends BaseEntity {

    public enum Purpose { CONFIRM, APPROVE, ACCEPT }

    public enum Method { PASSWORD, PIN }

    @Column(nullable = false, length = 100)
    private String entityType;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Purpose purpose;

    @Column(nullable = false, length = 50)
    private String roleCode;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Instant signedAt;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Method method;

    @Column(nullable = false, length = 64)
    private String hash;

    protected Signature() {
    }

    public Signature(String entityType, UUID entityId, Purpose purpose, String roleCode, UUID userId,
                     Instant signedAt, UUID deviceId, Method method, String hash) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.purpose = purpose;
        this.roleCode = roleCode;
        this.userId = userId;
        this.signedAt = signedAt;
        this.deviceId = deviceId;
        this.method = method;
        this.hash = hash;
    }

    public boolean isValidFor(String currentHash) {
        return hash.equals(currentHash);
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getSignedAt() {
        return signedAt;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public Method getMethod() {
        return method;
    }

    public String getHash() {
        return hash;
    }
}
