package vn.pxvb.workflow.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.pxvb.workflow.domain.AdjustmentRequest;
import vn.pxvb.workflow.domain.ApprovalDelegation;
import vn.pxvb.workflow.domain.ApprovalFlowDef;
import vn.pxvb.workflow.domain.ApprovalInstance;
import vn.pxvb.workflow.domain.ApprovalStep;
import vn.pxvb.workflow.domain.Signature;

/** Các Spring Data repository của module workflow, gom một chỗ để giữ số file nhỏ. */
public final class Repositories {

    private Repositories() {
    }

    public interface FlowDefRepository extends JpaRepository<ApprovalFlowDef, UUID> {
        Optional<ApprovalFlowDef> findByCode(String code);
    }

    public interface InstanceRepository extends JpaRepository<ApprovalInstance, UUID> {
        boolean existsByFlowCodeAndEntityIdAndStatus(String flowCode, UUID entityId, ApprovalInstance.Status status);

        List<ApprovalInstance> findByEntityTypeAndEntityIdOrderByStartedAtDesc(String entityType, UUID entityId);

        List<ApprovalInstance> findByIdInAndStatus(List<UUID> ids, ApprovalInstance.Status status);
    }

    public interface StepRepository extends JpaRepository<ApprovalStep, UUID> {
        List<ApprovalStep> findByInstanceIdOrderByStepNo(UUID instanceId);

        List<ApprovalStep> findByStatus(ApprovalStep.Status status);

        List<ApprovalStep> findByStatusAndDueAtBeforeAndOverdueNotifiedAtIsNull(ApprovalStep.Status status,
                                                                                Instant before);
    }

    public interface DelegationRepository extends JpaRepository<ApprovalDelegation, UUID> {
        List<ApprovalDelegation> findByToUserIdAndActiveTrue(UUID toUserId);

        List<ApprovalDelegation> findByFromUserIdOrderByValidFromDesc(UUID fromUserId);
    }

    public interface SignatureRepository extends JpaRepository<Signature, UUID> {
        List<Signature> findByEntityTypeAndEntityIdOrderBySignedAtAsc(String entityType, UUID entityId);
    }

    public interface AdjustmentRepository extends JpaRepository<AdjustmentRequest, UUID> {
        Page<AdjustmentRequest> findByStatus(AdjustmentRequest.Status status, Pageable pageable);

        Page<AdjustmentRequest> findByRequestedBy(UUID requestedBy, Pageable pageable);

        Page<AdjustmentRequest> findByApproverRoleCodeInAndStatus(java.util.Collection<String> roles,
                                                                  AdjustmentRequest.Status status,
                                                                  Pageable pageable);
    }
}
