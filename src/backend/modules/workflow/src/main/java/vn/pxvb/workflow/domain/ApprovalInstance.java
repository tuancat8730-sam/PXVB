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

/** Một lần duyệt của một đối tượng nghiệp vụ (sys_approval_instance). */
@Entity
@Table(name = "sys_approval_instance")
public class ApprovalInstance extends AuditableEntity {

    public enum Status { PENDING, APPROVED, REJECTED, RETURNED, CANCELLED }

    @Column(nullable = false, length = 50)
    private String flowCode;

    @Column(nullable = false, length = 100)
    private String entityType;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int currentStep;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false)
    private UUID startedBy;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant completedAt;

    @Column(length = 1000)
    private String note;

    protected ApprovalInstance() {
    }

    public ApprovalInstance(String flowCode, String entityType, UUID entityId, UUID startedBy, Instant startedAt,
                            int firstStep, String note) {
        this.flowCode = flowCode;
        this.entityType = entityType;
        this.entityId = entityId;
        this.startedBy = startedBy;
        this.startedAt = startedAt;
        this.currentStep = firstStep;
        this.note = note;
    }

    public void advanceTo(int step) {
        this.currentStep = step;
    }

    public void complete(Status finalStatus, Instant at, String note) {
        this.status = finalStatus;
        this.completedAt = at;
        if (note != null) {
            this.note = note;
        }
    }

    public String getFlowCode() {
        return flowCode;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public Status getStatus() {
        return status;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public UUID getStartedBy() {
        return startedBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getNote() {
        return note;
    }
}
