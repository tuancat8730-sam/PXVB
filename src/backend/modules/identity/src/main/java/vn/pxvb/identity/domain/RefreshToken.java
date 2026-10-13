package vn.pxvb.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.pxvb.common.domain.BaseEntity;

/** Refresh token xoay vòng, chỉ lưu hash SHA-256 (FR-CC-001). Không Auditable để tránh nhiễu audit. */
@Entity
@Table(name = "sys_refresh_token")
public class RefreshToken extends BaseEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 64)
    private String tokenHash;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID deviceId;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID replacedBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected RefreshToken() {
    }

    public RefreshToken(UUID userId, String tokenHash, UUID deviceId, Instant now, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.deviceId = deviceId;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void revoke(Instant now, UUID replacedBy) {
        this.revokedAt = now;
        this.replacedBy = replacedBy;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public String getTokenHash() {
        return tokenHash;
    }
}
