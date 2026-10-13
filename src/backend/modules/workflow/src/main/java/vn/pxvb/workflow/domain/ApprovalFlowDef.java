package vn.pxvb.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.pxvb.common.domain.AuditableEntity;

/**
 * Định nghĩa luồng duyệt cấu hình được (FR-CC-030). {@code steps} là JSON
 * {@code [{no, roleCode|userId, mode: ANY|ALL, slaMinutes, onTimeout: NOTIFY|ESCALATE}]}; các phần tử cùng
 * {@code no} tạo thành một giai đoạn song song.
 */
@Entity
@Table(name = "sys_approval_flow_def")
public class ApprovalFlowDef extends AuditableEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 30)
    private String module;

    @Column(nullable = false, length = 100)
    private String entityType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String steps;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected ApprovalFlowDef() {
    }

    public ApprovalFlowDef(String code, String module, String entityType, String steps) {
        this.code = code;
        this.module = module;
        this.entityType = entityType;
        this.steps = steps;
    }

    public void update(String module, String entityType, String steps, boolean active) {
        this.module = module;
        this.entityType = entityType;
        this.steps = steps;
        this.active = active;
    }

    public String getCode() {
        return code;
    }

    public String getModule() {
        return module;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getSteps() {
        return steps;
    }

    public boolean isActive() {
        return active;
    }
}
