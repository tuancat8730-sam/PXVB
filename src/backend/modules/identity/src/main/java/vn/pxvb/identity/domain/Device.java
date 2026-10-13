package vn.pxvb.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.pxvb.common.domain.AuditableEntity;

/** Thiết bị (tablet/IPC) đã đăng ký; kích hoạt một lần bằng mã (FR-CC-002). */
@Entity
@Table(name = "sys_device")
public class Device extends AuditableEntity {

    @Column(nullable = false, length = 50)
    private String deviceCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DevicePosition position;

    /** JSON array mã máy gắn với thiết bị. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String machineIds = "[]";

    private String appVersion;

    private Instant lastSeenAt;

    private Instant lastSyncAt;

    @Column(nullable = false)
    private int pendingCount;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    private Long clockOffsetMs;

    private String activationCodeHash;

    private Instant activationExpiresAt;

    private Instant activatedAt;

    protected Device() {
    }

    public Device(String deviceCode, String name, DevicePosition position, String machineIdsJson) {
        this.deviceCode = deviceCode;
        this.name = name;
        this.position = position;
        if (machineIdsJson != null) {
            this.machineIds = machineIdsJson;
        }
    }

    public void issueActivation(String codeHash, Instant expiresAt) {
        this.activationCodeHash = codeHash;
        this.activationExpiresAt = expiresAt;
    }

    public void markActivated(Instant now) {
        this.activationCodeHash = null;
        this.activationExpiresAt = null;
        this.activatedAt = now;
    }

    public boolean hasValidActivation(Instant now) {
        return activationCodeHash != null && activationExpiresAt != null && activationExpiresAt.isAfter(now);
    }

    public void touch(Instant now) {
        this.lastSeenAt = now;
    }

    public void recordSync(Instant now, int pending, Long offsetMs, String version) {
        this.lastSeenAt = now;
        this.lastSyncAt = now;
        this.pendingCount = pending;
        this.clockOffsetMs = offsetMs;
        if (version != null && !version.isBlank()) {
            this.appVersion = version;
        }
    }

    public Long getClockOffsetMs() {
        return clockOffsetMs;
    }

    public void deactivate() {
        this.active = false;
    }

    public String getDeviceCode() {
        return deviceCode;
    }

    public String getName() {
        return name;
    }

    public DevicePosition getPosition() {
        return position;
    }

    public String getMachineIds() {
        return machineIds;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public boolean isActive() {
        return active;
    }

    public String getActivationCodeHash() {
        return activationCodeHash;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }
}
