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
import vn.pxvb.common.domain.AuditableEntity;

/** Yêu cầu điều chỉnh dữ liệu đã khóa (FR-CC-011): đề nghị → duyệt → hệ thống tự áp giá trị + audit. */
@Entity
@Table(name = "sys_adjustment_request")
public class AdjustmentRequest extends AuditableEntity {

    public enum Status { PENDING, APPROVED, REJECTED, APPLIED, CANCELLED }

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 30)
    private String module;

    @Column(nullable = false, length = 100)
    private String entityType;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false)
    private UUID entityId;

    @Column(nullable = false, length = 200)
    private String fieldPath;

    @Column(length = 2000)
    private String oldValue;

    @Column(nullable = false, length = 2000)
    private String newValue;

    @Column(nullable = false, length = 1000)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String attachmentIds = "[]";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.PENDING;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false)
    private UUID requestedBy;

    @Column(nullable = false, length = 50)
    private String approverRoleCode;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID approvedBy;

    private Instant decidedAt;

    @Column(length = 1000)
    private String decisionNote;

    private Instant appliedAt;

    protected AdjustmentRequest() {
    }

    public AdjustmentRequest(String code, String module, String entityType, UUID entityId, String fieldPath,
                             String oldValue, String newValue, String reason, String attachmentIds,
                             UUID requestedBy, String approverRoleCode) {
        this.code = code;
        this.module = module;
        this.entityType = entityType;
        this.entityId = entityId;
        this.fieldPath = fieldPath;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.reason = reason;
        if (attachmentIds != null) {
            this.attachmentIds = attachmentIds;
        }
        this.requestedBy = requestedBy;
        this.approverRoleCode = approverRoleCode;
    }

    public void markApplied(UUID approver, Instant at, String note) {
        this.status = Status.APPLIED;
        this.approvedBy = approver;
        this.decidedAt = at;
        this.decisionNote = note;
        this.appliedAt = at;
    }

    public void reject(UUID approver, Instant at, String note) {
        this.status = Status.REJECTED;
        this.approvedBy = approver;
        this.decidedAt = at;
        this.decisionNote = note;
    }

    public void cancel(Instant at) {
        this.status = Status.CANCELLED;
        this.decidedAt = at;
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

    public UUID getEntityId() {
        return entityId;
    }

    public String getFieldPath() {
        return fieldPath;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getReason() {
        return reason;
    }

    public String getAttachmentIds() {
        return attachmentIds;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public String getApproverRoleCode() {
        return approverRoleCode;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }
}
