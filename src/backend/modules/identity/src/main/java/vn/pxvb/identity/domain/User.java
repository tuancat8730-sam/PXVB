package vn.pxvb.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.pxvb.common.domain.AuditableEntity;

/** Tài khoản người dùng (sys_user). Logic khóa tài khoản nằm trong entity để dễ kiểm thử đơn vị. */
@Entity
@Table(name = "sys_user")
public class User extends AuditableEntity {

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 200)
    private String fullName;

    @Column(length = 50)
    private String employeeCode;

    @Column(length = 50)
    private String badgeNo;

    @Column(nullable = false)
    private String passwordHash;

    private String pinHash;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID orgUnitId;

    private String email;

    private String phone;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int failedCount;

    private Instant lockedUntil;

    private Instant lastLoginAt;

    @Column(nullable = false)
    private boolean mustChangePassword;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuthSource authSource = AuthSource.LOCAL;

    protected User() {
    }

    public static User create(String username, String fullName, String employeeCode, String passwordHash) {
        User u = new User();
        u.username = username;
        u.fullName = fullName;
        u.employeeCode = employeeCode;
        u.passwordHash = passwordHash;
        u.mustChangePassword = true;
        return u;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Ghi nhận một lần đăng nhập sai; @return true nếu tài khoản vừa bị khóa (FR-CC-001). */
    public boolean registerFailure(Instant now, int maxFailures, Duration lockDuration) {
        failedCount++;
        if (failedCount >= maxFailures) {
            lockedUntil = now.plus(lockDuration);
            failedCount = 0;
            return true;
        }
        return false;
    }

    public void registerSuccess(Instant now) {
        failedCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void unlock() {
        failedCount = 0;
        lockedUntil = null;
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    public void changePassword(String newHash, boolean mustChange) {
        this.passwordHash = newHash;
        this.mustChangePassword = mustChange;
    }

    public void changePin(String pinHash) {
        this.pinHash = pinHash;
    }

    public void updateProfile(String fullName, String employeeCode, String badgeNo, UUID orgUnitId, String email,
                              String phone) {
        this.fullName = fullName;
        this.employeeCode = employeeCode;
        this.badgeNo = badgeNo;
        this.orgUnitId = orgUnitId;
        this.email = email;
        this.phone = phone;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getBadgeNo() {
        return badgeNo;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPinHash() {
        return pinHash;
    }

    public UUID getOrgUnitId() {
        return orgUnitId;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isActive() {
        return active;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public AuthSource getAuthSource() {
        return authSource;
    }
}
