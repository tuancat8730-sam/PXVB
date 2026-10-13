package vn.pxvb.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.pxvb.common.domain.AuditableEntity;
import vn.pxvb.common.security.DataScope;

/** Gán vai trò cho người dùng kèm phạm vi dữ liệu và hiệu lực (FR-CC-003). */
@Entity
@Table(name = "sys_user_role")
public class UserRole extends AuditableEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false)
    private UUID roleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DataScope.Type scopeType = DataScope.Type.ALL;

    @Column(length = 100)
    private String scopeValue;

    private Instant validFrom;

    private Instant validTo;

    protected UserRole() {
    }

    public UserRole(UUID userId, UUID roleId, DataScope.Type scopeType, String scopeValue, Instant validFrom,
                    Instant validTo) {
        this.userId = userId;
        this.roleId = roleId;
        this.scopeType = scopeType;
        this.scopeValue = scopeValue;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    public boolean isEffective(Instant now) {
        return (validFrom == null || !validFrom.isAfter(now)) && (validTo == null || validTo.isAfter(now));
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public DataScope.Type getScopeType() {
        return scopeType;
    }

    public String getScopeValue() {
        return scopeValue;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }
}
