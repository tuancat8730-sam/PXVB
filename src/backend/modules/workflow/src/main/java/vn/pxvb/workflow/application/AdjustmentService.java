package vn.pxvb.workflow.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.audit.AuditLogWriter;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.common.outbox.OutboxPublisher;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.common.sequence.NumberSequenceService;
import vn.pxvb.common.setting.SettingService;
import vn.pxvb.identity.contract.IdentityDirectory;
import vn.pxvb.workflow.application.Repositories.AdjustmentRepository;
import vn.pxvb.workflow.contract.AdjustmentApplier;
import vn.pxvb.workflow.contract.WorkflowEvents.AdjustmentApplied;
import vn.pxvb.workflow.domain.AdjustmentRequest;

/**
 * Điều chỉnh dữ liệu sau khi đã xác nhận/khóa (FR-CC-011..013): bắt buộc lý do, người duyệt ≠ người đề nghị
 * (BR-CC-003), khi duyệt tự áp giá trị trong cùng một giao dịch và ghi audit đủ trước/sau.
 */
@Service
public class AdjustmentService {

    private static final String DEFAULT_APPROVER_ROLE = "DEPUTY_DIRECTOR";

    private final AdjustmentRepository adjustments;
    private final Map<String, AdjustmentApplier> appliers = new HashMap<>();
    private final IdentityDirectory directory;
    private final ApprovalService approvals;
    private final SettingService settings;
    private final NumberSequenceService numbers;
    private final AuditLogWriter audit;
    private final OutboxPublisher outbox;
    private final ApplicationEventPublisher events;
    private final CurrentUser currentUser;
    private final Clock clock;

    public AdjustmentService(AdjustmentRepository adjustments, List<AdjustmentApplier> applierBeans,
                             IdentityDirectory directory, ApprovalService approvals, SettingService settings,
                             NumberSequenceService numbers, AuditLogWriter audit, OutboxPublisher outbox,
                             ApplicationEventPublisher events, CurrentUser currentUser, Clock clock) {
        this.adjustments = adjustments;
        applierBeans.forEach(a -> appliers.put(a.entityType(), a));
        this.directory = directory;
        this.approvals = approvals;
        this.settings = settings;
        this.numbers = numbers;
        this.audit = audit;
        this.outbox = outbox;
        this.events = events;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional
    public AdjustmentRequest create(String module, String entityType, UUID entityId, String fieldPath,
                                    String newValue, String reason, String attachmentIdsJson, UUID requestedBy) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cần nhập lý do điều chỉnh",
                    Map.of("reason", List.of("Vui lòng nhập lý do")));
        }
        if (newValue == null) {
            throw new BusinessException(ErrorCode.VALIDATION, "Thiếu giá trị đề nghị",
                    Map.of("newValue", List.of("Vui lòng nhập giá trị đề nghị")));
        }
        AdjustmentApplier applier = applierFor(entityType);
        AdjustmentApplier.Snapshot snapshot = applier.snapshot(entityId, fieldPath);
        if (snapshot.state() == null || !snapshot.state().isLocked()) {
            throw new BusinessException(ErrorCode.VALIDATION,
                    "Bản ghi chưa bị khóa, hãy sửa trực tiếp thay vì tạo yêu cầu điều chỉnh");
        }
        String code = numbers.next("adjustment", "DC", NumberSequenceService.ResetPolicy.DAILY);
        AdjustmentRequest request = adjustments.save(new AdjustmentRequest(code, module, entityType, entityId,
                fieldPath, snapshot.currentValue(), newValue, reason.trim(), attachmentIdsJson, requestedBy,
                approverRoleFor(module)));
        outbox.publish("adjustment.requested", Map.of(
                "requestId", request.getId().toString(),
                "code", code,
                "approverRoleCode", request.getApproverRoleCode(),
                "requestedBy", requestedBy.toString()));
        return request;
    }

    @Transactional
    public AdjustmentRequest approve(UUID id, UUID approverId, String note) {
        AdjustmentRequest request = pending(id);
        assertCanDecide(request, approverId);
        AdjustmentApplier applier = applierFor(request.getEntityType());
        AdjustmentApplier.Snapshot before = applier.snapshot(request.getEntityId(), request.getFieldPath());
        applier.apply(request.getEntityId(), request.getFieldPath(), request.getNewValue());
        Instant now = clock.instant();
        request.markApplied(approverId, now, note);
        audit.write(new AuditLogWriter.Entry(request.getModule(), request.getEntityType(),
                request.getEntityId().toString(), "ADJUST",
                List.of(new AuditLogWriter.FieldChange(request.getFieldPath(), before.currentValue(),
                        request.getNewValue())),
                request.getReason(), request.getRequestedBy(), approverId, currentUser.deviceId().orElse(null),
                request.getId()));
        events.publishEvent(new AdjustmentApplied(request.getId(), request.getModule(), request.getEntityType(),
                request.getEntityId(), request.getFieldPath(), request.getRequestedBy(), approverId));
        outbox.publish("adjustment.applied", Map.of(
                "requestId", request.getId().toString(),
                "module", request.getModule(),
                "entityType", request.getEntityType(),
                "entityId", request.getEntityId().toString(),
                "fieldPath", request.getFieldPath()));
        publishDecided(request);
        return request;
    }

    @Transactional
    public AdjustmentRequest reject(UUID id, UUID approverId, String note) {
        if (note == null || note.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cần nhập lý do từ chối",
                    Map.of("note", List.of("Vui lòng nhập lý do")));
        }
        AdjustmentRequest request = pending(id);
        assertCanDecide(request, approverId);
        request.reject(approverId, clock.instant(), note);
        publishDecided(request);
        return request;
    }

    @Transactional
    public AdjustmentRequest cancel(UUID id, UUID userId) {
        AdjustmentRequest request = pending(id);
        if (!request.getRequestedBy().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ người đề nghị mới được hủy yêu cầu");
        }
        request.cancel(clock.instant());
        return request;
    }

    @Transactional(readOnly = true)
    public AdjustmentRequest get(UUID id) {
        return adjustments.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Page<AdjustmentRequest> mine(UUID userId, Pageable pageable) {
        return adjustments.findByRequestedBy(userId, pageable);
    }

    /** Yêu cầu đang chờ người dùng duyệt (theo vai trò, kể cả vai trò của người đã ủy quyền). */
    @Transactional(readOnly = true)
    public Page<AdjustmentRequest> awaitingMyApproval(UUID userId, Pageable pageable) {
        Set<String> roles = new java.util.HashSet<>();
        approvals.actors(userId).forEach(actor -> roles.addAll(directory.roleCodesOf(actor)));
        if (roles.isEmpty()) {
            return Page.empty(pageable);
        }
        return adjustments.findByApproverRoleCodeInAndStatus(roles, AdjustmentRequest.Status.PENDING, pageable);
    }

    private AdjustmentRequest pending(UUID id) {
        AdjustmentRequest request = get(id);
        if (request.getStatus() != AdjustmentRequest.Status.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Yêu cầu điều chỉnh đã được xử lý");
        }
        return request;
    }

    private void assertCanDecide(AdjustmentRequest request, UUID approverId) {
        if (request.getRequestedBy().equals(approverId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Người đề nghị không được tự duyệt yêu cầu của mình");
        }
        boolean allowed = approvals.actors(approverId).stream()
                .anyMatch(actor -> directory.roleCodesOf(actor).contains(request.getApproverRoleCode()));
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Cần vai trò " + request.getApproverRoleCode() + " để duyệt yêu cầu này");
        }
    }

    private AdjustmentApplier applierFor(String entityType) {
        AdjustmentApplier applier = appliers.get(entityType);
        if (applier == null) {
            throw new BusinessException(ErrorCode.VALIDATION,
                    "Chưa hỗ trợ điều chỉnh đối tượng loại " + entityType);
        }
        return applier;
    }

    /** Vai trò duyệt cấu hình được theo phân hệ: {@code workflow.adjustment.approver.<MODULE>} hoặc {@code .default}. */
    private String approverRoleFor(String module) {
        return settings.getJson("workflow.adjustment.approver." + module)
                .or(() -> settings.getJson("workflow.adjustment.approver.default"))
                .map(v -> v.replace("\"", "").trim()).filter(v -> !v.isEmpty()).orElse(DEFAULT_APPROVER_ROLE);
    }

    private void publishDecided(AdjustmentRequest request) {
        outbox.publish("adjustment.decided", Map.of(
                "requestId", request.getId().toString(),
                "code", request.getCode(),
                "status", request.getStatus().name(),
                "requestedBy", request.getRequestedBy().toString()));
    }
}
