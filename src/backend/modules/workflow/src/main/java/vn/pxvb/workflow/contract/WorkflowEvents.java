package vn.pxvb.workflow.contract;

import java.util.UUID;

/**
 * Sự kiện đồng bộ trong tiến trình (Spring {@code ApplicationEvent}, phát trong cùng giao dịch) để module nghiệp
 * vụ phản ứng ngay, ví dụ kích hoạt kế hoạch khi duyệt xong. Cùng nội dung cũng được ghi outbox
 * ({@code approval.completed}, {@code adjustment.applied}) cho consumer bất đồng bộ.
 */
public final class WorkflowEvents {

    private WorkflowEvents() {
    }

    /** {@code status} ∈ APPROVED | REJECTED | RETURNED | CANCELLED. */
    public record ApprovalCompleted(UUID instanceId, String flowCode, String entityType, UUID entityId,
                                    String status, UUID startedBy) {
    }

    public record AdjustmentApplied(UUID requestId, String module, String entityType, UUID entityId,
                                    String fieldPath, UUID requestedBy, UUID approvedBy) {
    }
}
