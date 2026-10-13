package vn.pxvb.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.pxvb.common.domain.AuditableEntity;

/** Đơn vị tổ chức (PXVB, PXCD, KTCD, KHVT, TCKT, DBCL, KHO_CK, BGD). */
@Entity
@Table(name = "sys_org_unit")
public class OrgUnit extends AuditableEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID parentId;

    protected OrgUnit() {
    }

    public OrgUnit(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public UUID getParentId() {
        return parentId;
    }
}
