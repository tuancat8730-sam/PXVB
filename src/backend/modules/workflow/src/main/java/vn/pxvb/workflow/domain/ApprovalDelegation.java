package vn.pxvb.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.pxvb.common.domain.AuditableEntity;

/** Ủy quyền duyệt tạm thời có thời hạn (FR-CC-030). */
@Entity
@Table(name = "sys_approval_delegation")
public class ApprovalDelegation extends AuditableEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false)
    private UUID fromUserId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false)
    private UUID toUserId;

    @Column(nullable = false)
    private Instant validFrom;

    @Column(nullable = false)
    private Instant validTo;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected ApprovalDelegation() {
    }

    public ApprovalDelegation(UUID fromUserId, UUID toUserId, Instant validFrom, Instant validTo) {
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    public boolean isEffective(Instant now) {
        return active && !validFrom.isAfter(now) && validTo.isAfter(now);
    }

    public void revoke() {
        this.active = false;
    }

    public UUID getFromUserId() {
        return fromUserId;
    }

    public UUID getToUserId() {
        return toUserId;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public boolean isActive() {
        return active;
    }
}
