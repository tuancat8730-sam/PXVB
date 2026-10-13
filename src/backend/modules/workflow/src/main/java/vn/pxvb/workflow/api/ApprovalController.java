package vn.pxvb.workflow.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.workflow.application.ApprovalService;
import vn.pxvb.workflow.application.ApprovalService.ApprovalView;
import vn.pxvb.workflow.application.ApprovalService.Decision;
import vn.pxvb.workflow.domain.ApprovalDelegation;
import vn.pxvb.workflow.domain.ApprovalFlowDef;
import vn.pxvb.workflow.domain.ApprovalInstance;
import vn.pxvb.workflow.domain.ApprovalStep;

/** Phê duyệt, cấu hình luồng và ủy quyền (CC-05, FR-CC-030). */
@RestController
@RequestMapping("/api/v1/workflow")
public class ApprovalController {

    public record StepDto(UUID id, int stepNo, String roleCode, UUID assigneeUserId, String mode, String status,
                          UUID decidedBy, UUID actingFor, Instant decidedAt, String note, Instant dueAt) {
        static StepDto from(ApprovalStep s) {
            return new StepDto(s.getId(), s.getStepNo(), s.getRoleCode(), s.getAssigneeUserId(), s.getMode().name(),
                    s.getStatus().name(), s.getDecidedBy(), s.getActingFor(), s.getDecidedAt(), s.getNote(),
                    s.getDueAt());
        }
    }

    public record ApprovalDto(UUID id, String flowCode, String entityType, UUID entityId, String status,
                              int currentStep, UUID startedBy, Instant startedAt, Instant completedAt, String note,
                              List<StepDto> steps) {
        static ApprovalDto from(ApprovalView v) {
            ApprovalInstance i = v.instance();
            return new ApprovalDto(i.getId(), i.getFlowCode(), i.getEntityType(), i.getEntityId(),
                    i.getStatus().name(), i.getCurrentStep(), i.getStartedBy(), i.getStartedAt(),
                    i.getCompletedAt(), i.getNote(), v.steps().stream().map(StepDto::from).toList());
        }
    }

    public record StartRequest(@NotBlank(message = "Vui lòng chọn luồng duyệt") String flowCode,
                               @NotNull(message = "Thiếu đối tượng cần duyệt") UUID entityId, String note) {
    }

    public record DecisionRequest(String note) {
    }

    public record FlowDto(String code, String module, String entityType, String steps, boolean active) {
        static FlowDto from(ApprovalFlowDef f) {
            return new FlowDto(f.getCode(), f.getModule(), f.getEntityType(), f.getSteps(), f.isActive());
        }
    }

    public record FlowRequest(@NotBlank String module, @NotBlank String entityType, @NotBlank String steps,
                              boolean active) {
    }

    public record DelegationRequest(@NotNull UUID toUserId, @NotNull Instant validFrom, @NotNull Instant validTo) {
    }

    public record DelegationDto(UUID id, UUID fromUserId, UUID toUserId, Instant validFrom, Instant validTo,
                                boolean active) {
        static DelegationDto from(ApprovalDelegation d) {
            return new DelegationDto(d.getId(), d.getFromUserId(), d.getToUserId(), d.getValidFrom(),
                    d.getValidTo(), d.isActive());
        }
    }

    private final ApprovalService service;
    private final CurrentUser currentUser;

    public ApprovalController(ApprovalService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/approvals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('workflow.approval.start')")
    public ApprovalDto start(@Valid @RequestBody StartRequest req) {
        return ApprovalDto.from(service.start(req.flowCode(), req.entityId(), currentUser.require().userId(),
                req.note()));
    }

    /** Yêu cầu đang chờ tôi duyệt (kể cả theo ủy quyền). */
    @GetMapping("/approvals/pending")
    @PreAuthorize("isAuthenticated()")
    public List<ApprovalDto> pending() {
        return service.pendingFor(currentUser.require().userId()).stream().map(ApprovalDto::from).toList();
    }

    @GetMapping("/approvals")
    @PreAuthorize("hasAuthority('workflow.approval.view')")
    public List<ApprovalDto> byEntity(@RequestParam String entityType, @RequestParam UUID entityId) {
        return service.forEntity(entityType, entityId).stream().map(ApprovalDto::from).toList();
    }

    @GetMapping("/approvals/{id}")
    @PreAuthorize("hasAuthority('workflow.approval.view')")
    public ApprovalDto get(@PathVariable UUID id) {
        return ApprovalDto.from(service.get(id));
    }

    @PostMapping("/approvals/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public ApprovalDto approve(@PathVariable UUID id, @RequestBody(required = false) DecisionRequest req) {
        return decide(id, Decision.APPROVE, req);
    }

    @PostMapping("/approvals/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    public ApprovalDto reject(@PathVariable UUID id, @RequestBody(required = false) DecisionRequest req) {
        return decide(id, Decision.REJECT, req);
    }

    @PostMapping("/approvals/{id}/return")
    @PreAuthorize("isAuthenticated()")
    public ApprovalDto giveBack(@PathVariable UUID id, @RequestBody(required = false) DecisionRequest req) {
        return decide(id, Decision.RETURN, req);
    }

    @PostMapping("/approvals/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ApprovalDto cancel(@PathVariable UUID id) {
        var principal = currentUser.require();
        return ApprovalDto.from(service.cancel(id, principal.userId(),
                principal.permissions().contains("workflow.approval.manage")));
    }

    private ApprovalDto decide(UUID id, Decision decision, DecisionRequest req) {
        return ApprovalDto.from(service.decide(id, currentUser.require().userId(), decision,
                req == null ? null : req.note()));
    }

    // --- Cấu hình luồng ---

    @GetMapping("/flows")
    @PreAuthorize("hasAuthority('workflow.approval.view')")
    public List<FlowDto> flows() {
        return service.listFlows().stream().map(FlowDto::from).toList();
    }

    @PutMapping("/flows/{code}")
    @PreAuthorize("hasAuthority('workflow.flow.config')")
    public FlowDto upsertFlow(@PathVariable String code, @Valid @RequestBody FlowRequest req) {
        return FlowDto.from(service.upsertFlow(code, req.module(), req.entityType(), req.steps(), req.active()));
    }

    // --- Ủy quyền ---

    @GetMapping("/delegations/mine")
    @PreAuthorize("hasAuthority('workflow.delegation.manage')")
    public List<DelegationDto> myDelegations() {
        return service.myDelegations(currentUser.require().userId()).stream().map(DelegationDto::from).toList();
    }

    @PostMapping("/delegations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('workflow.delegation.manage')")
    public DelegationDto delegate(@Valid @RequestBody DelegationRequest req) {
        return DelegationDto.from(service.delegate(currentUser.require().userId(), req.toUserId(),
                req.validFrom(), req.validTo()));
    }

    @DeleteMapping("/delegations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('workflow.delegation.manage')")
    public void revoke(@PathVariable UUID id) {
        service.revokeDelegation(id, currentUser.require().userId());
    }
}
