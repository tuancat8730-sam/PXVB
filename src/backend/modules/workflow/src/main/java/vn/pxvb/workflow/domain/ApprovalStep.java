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
import vn.pxvb.common.domain.BaseEntity;

/** Một người/vai trò duyệt trong một giai đoạn của {@link ApprovalInstance}. */
@Entity
@Table(name = "sys_approval_step")
public class ApprovalStep extends BaseEntity {

    public enum Status { WAITING, PENDING, APPROVED, REJECTED, RETURNED, SKIPPED }

    public enum Mode { ANY, ALL }

    public enum OnTimeout { NOTIFY, ESCALATE }

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false)
    private UUID instanceId;

    @Column(nullable = false)
    private int stepNo;

    @Column(length = 50)
    private String roleCode;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID assigneeUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Mode mode = Mode.ANY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.WAITING;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID decidedBy;

    /** Người ủy quyền khi bước được xử lý qua ủy quyền tạm thời (FR-CC-030). */
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID actingFor;

    private Instant decidedAt;

    @Column(length = 1000)
    private String note;

    private Integer slaMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OnTimeout onTimeout = OnTimeout.NOTIFY;

    private Instant dueAt;

    private Instant overdueNotifiedAt;

    protected ApprovalStep() {
    }

    public ApprovalStep(UUID instanceId, int stepNo, String roleCode, UUID assigneeUserId, Mode mode,
                        Integer slaMinutes, OnTimeout onTimeout) {
        this.instanceId = instanceId;
        this.stepNo = stepNo;
        this.roleCode = roleCode;
        this.assigneeUserId = assigneeUserId;
        this.mode = mode;
        this.slaMinutes = slaMinutes;
        this.onTimeout = onTimeout;
    }

    public void activate(Instant now) {
        this.status = Status.PENDING;
        if (slaMinutes != null) {
            this.dueAt = now.plusSeconds(slaMinutes * 60L);
        }
    }

    public void decide(Status result, UUID by, UUID actingFor, Instant at, String note) {
        this.status = result;
        this.decidedBy = by;
        this.actingFor = actingFor;
        this.decidedAt = at;
        this.note = note;
    }

    public void skip() {
        this.status = Status.SKIPPED;
    }

    public void markOverdueNotified(Instant at) {
        this.overdueNotifiedAt = at;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public int getStepNo() {
        return stepNo;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public UUID getAssigneeUserId() {
        return assigneeUserId;
    }

    public Mode getMode() {
        return mode;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public UUID getActingFor() {
        return actingFor;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getNote() {
        return note;
    }

    public Integer getSlaMinutes() {
        return slaMinutes;
    }

    public OnTimeout getOnTimeout() {
        return onTimeout;
    }

    public Instant getDueAt() {
        return dueAt;
    }
}
