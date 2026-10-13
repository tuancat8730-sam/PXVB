package vn.pxvb.common.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Khóa chính UUID v7 (sinh phía ứng dụng) lưu BINARY(16) và RowVersion cho optimistic locking (SDD §2.5).
 * {@code rowVersion} là wrapper để Spring Data nhận biết entity mới (version == null).
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    @Version
    private Long rowVersion;

    protected BaseEntity() {
        this.id = UuidCreator.getTimeOrderedEpoch();
    }

    protected BaseEntity(UUID id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public UUID getId() {
        return id;
    }

    public Long getRowVersion() {
        return rowVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || org.hibernate.Hibernate.getClass(this) != org.hibernate.Hibernate.getClass(o)) {
            return false;
        }
        return id.equals(((BaseEntity) o).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
