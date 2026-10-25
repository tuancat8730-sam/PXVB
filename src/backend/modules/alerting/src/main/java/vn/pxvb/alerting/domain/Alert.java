package vn.pxvb.alerting.domain;

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
import vn.pxvb.common.domain.StateMachine;

/**
 * Cảnh báo (sys_alert). Vòng đời OPEN → ACK → IN_PROGRESS → RESOLVED → CLOSED (FR-CC-051); mọi chuyển trạng
 * thái được ghi vào {@link AlertHistory}.
 */
@Entity
@Table(name = "sys_alert")
public class Alert extends BaseEntity {

    public enum Severity { INFO, WARN, CRITICAL }

    public enum Status { OPEN, ACK, IN_PROGRESS, RESOLVED, CLOSED }

    public static final StateMachine<Status> LIFECYCLE = StateMachine.<Status>builder()
            .allow(Status.OPEN, Status.ACK)
            .allow(Status.ACK, Status.IN_PROGRESS)
            .allow(Status.ACK, Status.RESOLVED)
            .allow(Status.IN_PROGRESS, Status.RESOLVED)
            .allow(Status.RESOLVED, Status.IN_PROGRESS)
            .allow(Status.RESOLVED, Status.CLOSED)
            .build();

    @Column(nullable = false, length = 100)
    private String ruleCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Severity severity;

    @Column(nullable = false, length = 30)
    private String module;

    @Column(length = 100)
    private String subjectType;

    @Column(length = 100)
    private String subjectId;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID machineId;

    private String lineCode;

    /** Phiên bản rule lúc raise (L67); null với alert tạo trước khi có snapshot. */
    private Integer ruleVersion;

    /** Snapshot cấu hình rule lúc raise (JSON): mức, cửa sổ khử trùng, người nhận, leo thang. */
    @JdbcTypeCode(SqlTypes.JSON)
    private String ruleSnapshot;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID shiftSessionId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 2000)
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload = "{}";

    @Column(nullable = false, length = 200)
    private String dedupKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.OPEN;

    @Column(nullable = false)
    private Instant raisedAt;

    @Column(nullable = false)
    private Instant lastOccurredAt;

    @Column(nullable = false)
    private int occurrenceCount = 1;

    private Instant ackAt;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID ackBy;

    private Instant resolvedAt;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID resolvedBy;

    @Column(length = 200)
    private String cause;

    @Column(length = 1000)
    private String resolutionNote;

    @Column(nullable = false)
    private int escalationLevel;

    protected Alert() {
    }

    public Alert(String ruleCode, Severity severity, String module, String subjectType, String subjectId,
                 UUID machineId, String lineCode, UUID shiftSessionId, String title, String message,
                 String payloadJson, String dedupKey, Instant now, Integer ruleVersion, String ruleSnapshot) {
        this.ruleCode = ruleCode;
        this.severity = severity;
        this.module = module;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.machineId = machineId;
        this.lineCode = lineCode;
        this.ruleVersion = ruleVersion;
        this.ruleSnapshot = ruleSnapshot;
        this.shiftSessionId = shiftSessionId;
        this.title = title;
        this.message = message;
        if (payloadJson != null) {
            this.payload = payloadJson;
        }
        this.dedupKey = dedupKey;
        this.raisedAt = now;
        this.lastOccurredAt = now;
    }

    public boolean isActive() {
        return status == Status.OPEN || status == Status.ACK || status == Status.IN_PROGRESS;
    }

    public void recordRecurrence(Instant now, String message, String payloadJson) {
        this.occurrenceCount++;
        this.lastOccurredAt = now;
        if (message != null) {
            this.message = message;
        }
        if (payloadJson != null) {
            this.payload = payloadJson;
        }
    }

    public Status transitionTo(Status target, UUID by, Instant now) {
        this.status = LIFECYCLE.transition(status, target);
        switch (target) {
            case ACK -> {
                this.ackAt = now;
                this.ackBy = by;
            }
            case RESOLVED -> {
                this.resolvedAt = now;
                this.resolvedBy = by;
            }
            default -> { }
        }
        return status;
    }

    /** Nâng mức (chỉ tăng, không hạ). @return true nếu mức thực sự thay đổi. */
    public boolean raiseSeverity(Severity requested) {
        if (requested.compareTo(this.severity) <= 0) {
            return false;
        }
        this.severity = requested;
        return true;
    }

    public void resolutionDetails(String cause, String note) {
        this.cause = cause;
        this.resolutionNote = note;
    }

    public void escalate() {
        this.escalationLevel++;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getModule() {
        return module;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public UUID getMachineId() {
        return machineId;
    }

    /** Dây chuyền của máy tại thời điểm raise (snapshot, FR-CC-006); null nếu không có máy hoặc máy chưa gán dây chuyền. */
    public Integer getRuleVersion() {
        return ruleVersion;
    }

    public String getRuleSnapshot() {
        return ruleSnapshot;
    }

    public String getLineCode() {
        return lineCode;
    }

    public UUID getShiftSessionId() {
        return shiftSessionId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getPayload() {
        return payload;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }

    public Instant getLastOccurredAt() {
        return lastOccurredAt;
    }

    public int getOccurrenceCount() {
        return occurrenceCount;
    }

    public Instant getAckAt() {
        return ackAt;
    }

    public UUID getAckBy() {
        return ackBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public UUID getResolvedBy() {
        return resolvedBy;
    }

    public String getCause() {
        return cause;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public int getEscalationLevel() {
        return escalationLevel;
    }
}
