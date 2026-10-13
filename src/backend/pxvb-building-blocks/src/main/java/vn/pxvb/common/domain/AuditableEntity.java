package vn.pxvb.common.domain;

import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Cột chung CreatedAt/CreatedBy/UpdatedAt/UpdatedBy (SDD §2.5). Thời gian là UTC. */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity extends BaseEntity implements Auditable {

    @CreatedDate
    private Instant createdAt;

    @CreatedBy
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID createdBy;

    @LastModifiedDate
    private Instant updatedAt;

    @LastModifiedBy
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID updatedBy;

    protected AuditableEntity() {
        super();
    }

    protected AuditableEntity(UUID id) {
        super(id);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
