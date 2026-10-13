package vn.pxvb.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.common.outbox.OutboxPublisher;
import vn.pxvb.identity.contract.IdentityDirectory;
import vn.pxvb.workflow.application.Repositories.DelegationRepository;
import vn.pxvb.workflow.application.Repositories.FlowDefRepository;
import vn.pxvb.workflow.application.Repositories.InstanceRepository;
import vn.pxvb.workflow.application.Repositories.StepRepository;
import vn.pxvb.workflow.contract.WorkflowEvents.ApprovalCompleted;
import vn.pxvb.workflow.domain.ApprovalDelegation;
import vn.pxvb.workflow.domain.ApprovalFlowDef;
import vn.pxvb.workflow.domain.ApprovalInstance;
import vn.pxvb.workflow.domain.ApprovalStep;

/**
 * Engine phê duyệt tuần tự/song song cấu hình bằng JSON (FR-CC-030): ANY/ALL theo giai đoạn, trả lại,
 * ủy quyền tạm thời, SLA. Người đề nghị không được tự duyệt (BR-CC-003).
 */
@Service
public class ApprovalService {

    public record StepSpec(int no, String roleCode, UUID userId, String mode, Integer slaMinutes, String onTimeout) {
    }

    public enum Decision { APPROVE, REJECT, RETURN }

    public record ApprovalView(ApprovalInstance instance, List<ApprovalStep> steps) {
    }

    private final FlowDefRepository flows;
    private final InstanceRepository instances;
    private final StepRepository steps;
    private final DelegationRepository delegations;
    private final IdentityDirectory directory;
    private final OutboxPublisher outbox;
    private final ApplicationEventPublisher events;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ApprovalService(FlowDefRepository flows, InstanceRepository instances, StepRepository steps,
                           DelegationRepository delegations, IdentityDirectory directory, OutboxPublisher outbox,
                           ApplicationEventPublisher events, ObjectMapper mapper, Clock clock) {
        this.flows = flows;
        this.instances = instances;
        this.steps = steps;
        this.delegations = delegations;
        this.directory = directory;
        this.outbox = outbox;
        this.events = events;
        this.mapper = mapper;
        this.clock = clock;
    }

    // ---------- Khởi tạo ----------

    @Transactional
    public ApprovalView start(String flowCode, UUID entityId, UUID startedBy, String note) {
        ApprovalFlowDef def = flows.findByCode(flowCode).filter(ApprovalFlowDef::isActive).orElseThrow(
                () -> new BusinessException(ErrorCode.VALIDATION, "Luồng duyệt không tồn tại hoặc đã tắt: " + flowCode));
        if (instances.existsByFlowCodeAndEntityIdAndStatus(flowCode, entityId, ApprovalInstance.Status.PENDING)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Đối tượng đang có yêu cầu duyệt chưa hoàn tất");
        }
        List<StepSpec> specs = parseSteps(def.getSteps());
        int first = specs.stream().mapToInt(StepSpec::no).min().orElseThrow();
        Instant now = clock.instant();
        ApprovalInstance instance = instances.save(new ApprovalInstance(flowCode, def.getEntityType(), entityId,
                startedBy, now, first, note));
        List<ApprovalStep> created = new ArrayList<>();
        for (StepSpec spec : specs) {
            ApprovalStep step = new ApprovalStep(instance.getId(), spec.no(), spec.roleCode(), spec.userId(),
                    "ALL".equalsIgnoreCase(spec.mode()) ? ApprovalStep.Mode.ALL : ApprovalStep.Mode.ANY,
                    spec.slaMinutes(), "ESCALATE".equalsIgnoreCase(spec.onTimeout())
                    ? ApprovalStep.OnTimeout.ESCALATE : ApprovalStep.OnTimeout.NOTIFY);
            if (spec.no() == first) {
                step.activate(now);
            }
            created.add(steps.save(step));
        }
        publishRequested(instance, created);
        return new ApprovalView(instance, created);
    }

    // ---------- Quyết định ----------

    @Transactional
    public ApprovalView decide(UUID instanceId, UUID userId, Decision decision, String note) {
        ApprovalInstance instance = instances.findById(instanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (instance.getStatus() != ApprovalInstance.Status.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Yêu cầu duyệt đã được xử lý xong");
        }
        if (instance.getStartedBy().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Người đề nghị không được tự duyệt yêu cầu của mình");
        }
        if (decision != Decision.APPROVE && (note == null || note.isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cần nhập lý do khi từ chối hoặc trả lại",
                    Map.of("note", List.of("Vui lòng nhập lý do")));
        }
        Instant now = clock.instant();
        List<ApprovalStep> all = steps.findByInstanceIdOrderByStepNo(instanceId);
        List<ApprovalStep> stage = all.stream().filter(s -> s.getStepNo() == instance.getCurrentStep()).toList();
        if (stage.stream().anyMatch(s -> userId.equals(s.getDecidedBy()))) {
            throw new BusinessException(ErrorCode.CONFLICT, "Bạn đã xử lý bước này");
        }
        ApprovalStep mine = null;
        UUID actingFor = null;
        for (ApprovalStep s : stage) {
            if (s.getStatus() != ApprovalStep.Status.PENDING) {
                continue;
            }
            Optional<UUID> actor = eligibleActor(userId, s, now);
            if (actor.isPresent()) {
                mine = s;
                actingFor = actor.get().equals(userId) ? null : actor.get();
                break;
            }
        }
        if (mine == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có thẩm quyền duyệt bước này");
        }

        switch (decision) {
            case APPROVE -> {
                mine.decide(ApprovalStep.Status.APPROVED, userId, actingFor, now, note);
                advanceIfStageDone(instance, mine, stage, all, now);
            }
            case REJECT -> {
                mine.decide(ApprovalStep.Status.REJECTED, userId, actingFor, now, note);
                closeAsNegative(instance, all, ApprovalInstance.Status.REJECTED, now, note);
            }
            case RETURN -> {
                mine.decide(ApprovalStep.Status.RETURNED, userId, actingFor, now, note);
                closeAsNegative(instance, all, ApprovalInstance.Status.RETURNED, now, note);
            }
            default -> throw new IllegalStateException();
        }
        return new ApprovalView(instance, all);
    }

    @Transactional
    public ApprovalView cancel(UUID instanceId, UUID userId, boolean canManageOthers) {
        ApprovalInstance instance = instances.findById(instanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (instance.getStatus() != ApprovalInstance.Status.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Yêu cầu duyệt đã được xử lý xong");
        }
        if (!instance.getStartedBy().equals(userId) && !canManageOthers) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chỉ người đề nghị mới được hủy yêu cầu duyệt");
        }
        List<ApprovalStep> all = steps.findByInstanceIdOrderByStepNo(instanceId);
        all.stream().filter(s -> s.getStatus() == ApprovalStep.Status.PENDING
                || s.getStatus() == ApprovalStep.Status.WAITING).forEach(ApprovalStep::skip);
        complete(instance, ApprovalInstance.Status.CANCELLED, clock.instant(), null);
        return new ApprovalView(instance, all);
    }

    private void advanceIfStageDone(ApprovalInstance instance, ApprovalStep decided, List<ApprovalStep> stage,
                                    List<ApprovalStep> all, Instant now) {
        boolean stageDone = decided.getMode() == ApprovalStep.Mode.ANY
                || stage.stream().allMatch(s -> s.getStatus() == ApprovalStep.Status.APPROVED);
        if (!stageDone) {
            return;
        }
        stage.stream().filter(s -> s.getStatus() == ApprovalStep.Status.PENDING).forEach(ApprovalStep::skip);
        OptionalInt next = all.stream().mapToInt(ApprovalStep::getStepNo)
                .filter(n -> n > instance.getCurrentStep()).min();
        if (next.isEmpty()) {
            complete(instance, ApprovalInstance.Status.APPROVED, now, null);
            return;
        }
        instance.advanceTo(next.getAsInt());
        List<ApprovalStep> activated = all.stream().filter(s -> s.getStepNo() == next.getAsInt()).toList();
        activated.forEach(s -> s.activate(now));
        publishRequested(instance, activated);
    }

    private void closeAsNegative(ApprovalInstance instance, List<ApprovalStep> all,
                                 ApprovalInstance.Status finalStatus, Instant now, String note) {
        all.stream().filter(s -> s.getStatus() == ApprovalStep.Status.PENDING
                || s.getStatus() == ApprovalStep.Status.WAITING).forEach(ApprovalStep::skip);
        complete(instance, finalStatus, now, note);
    }

    private void complete(ApprovalInstance instance, ApprovalInstance.Status status, Instant now, String note) {
        instance.complete(status, now, note);
        events.publishEvent(new ApprovalCompleted(instance.getId(), instance.getFlowCode(), instance.getEntityType(),
                instance.getEntityId(), status.name(), instance.getStartedBy()));
        outbox.publish("approval.completed", Map.of(
                "instanceId", instance.getId().toString(),
                "flowCode", instance.getFlowCode(),
                "entityType", instance.getEntityType(),
                "entityId", instance.getEntityId().toString(),
                "status", status.name(),
                "startedBy", instance.getStartedBy().toString()));
    }

    private void publishRequested(ApprovalInstance instance, List<ApprovalStep> stageSteps) {
        List<String> roles = stageSteps.stream().filter(s -> s.getStatus() == ApprovalStep.Status.PENDING
                        || s.getStatus() == ApprovalStep.Status.WAITING && s.getStepNo() == instance.getCurrentStep())
                .map(ApprovalStep::getRoleCode).filter(r -> r != null).distinct().toList();
        List<String> users = stageSteps.stream().map(ApprovalStep::getAssigneeUserId).filter(u -> u != null)
                .map(UUID::toString).distinct().toList();
        outbox.publish("approval.requested", Map.of(
                "instanceId", instance.getId().toString(),
                "flowCode", instance.getFlowCode(),
                "entityType", instance.getEntityType(),
                "entityId", instance.getEntityId().toString(),
                "stepNo", instance.getCurrentStep(),
                "roleCodes", roles,
                "userIds", users,
                "startedBy", instance.getStartedBy().toString()));
    }

    // ---------- Truy vấn ----------

    @Transactional(readOnly = true)
    public ApprovalView get(UUID id) {
        ApprovalInstance instance = instances.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return new ApprovalView(instance, steps.findByInstanceIdOrderByStepNo(id));
    }

    @Transactional(readOnly = true)
    public List<ApprovalView> forEntity(String entityType, UUID entityId) {
        return instances.findByEntityTypeAndEntityIdOrderByStartedAtDesc(entityType, entityId).stream()
                .map(i -> new ApprovalView(i, steps.findByInstanceIdOrderByStepNo(i.getId()))).toList();
    }

    /** Các yêu cầu đang chờ người dùng này duyệt (kể cả theo ủy quyền); loại yêu cầu do chính họ khởi tạo. */
    @Transactional(readOnly = true)
    public List<ApprovalView> pendingFor(UUID userId) {
        Instant now = clock.instant();
        Set<UUID> instanceIds = steps.findByStatus(ApprovalStep.Status.PENDING).stream()
                .filter(s -> eligibleActor(userId, s, now).isPresent())
                .map(ApprovalStep::getInstanceId).collect(Collectors.toSet());
        if (instanceIds.isEmpty()) {
            return List.of();
        }
        return instances.findByIdInAndStatus(new ArrayList<>(instanceIds), ApprovalInstance.Status.PENDING).stream()
                .filter(i -> !i.getStartedBy().equals(userId))
                .map(i -> new ApprovalView(i, steps.findByInstanceIdOrderByStepNo(i.getId()))).toList();
    }

    // ---------- Ủy quyền ----------

    /** Danh sách danh tính mà người dùng được phép hành động thay: chính họ + người đã ủy quyền còn hiệu lực. */
    @Transactional(readOnly = true)
    public Set<UUID> actors(UUID userId) {
        Instant now = clock.instant();
        Set<UUID> result = new LinkedHashSet<>();
        result.add(userId);
        delegations.findByToUserIdAndActiveTrue(userId).stream().filter(d -> d.isEffective(now))
                .forEach(d -> result.add(d.getFromUserId()));
        return result;
    }

    @Transactional
    public ApprovalDelegation delegate(UUID fromUserId, UUID toUserId, Instant validFrom, Instant validTo) {
        if (fromUserId.equals(toUserId)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Không thể ủy quyền cho chính mình");
        }
        if (!validTo.isAfter(validFrom)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Thời hạn ủy quyền không hợp lệ");
        }
        if (directory.fullNameOf(toUserId).isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Người được ủy quyền không tồn tại");
        }
        return delegations.save(new ApprovalDelegation(fromUserId, toUserId, validFrom, validTo));
    }

    @Transactional
    public void revokeDelegation(UUID id, UUID userId) {
        ApprovalDelegation d = delegations.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!d.getFromUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        d.revoke();
    }

    @Transactional(readOnly = true)
    public List<ApprovalDelegation> myDelegations(UUID userId) {
        return delegations.findByFromUserIdOrderByValidFromDesc(userId);
    }

    private Optional<UUID> eligibleActor(UUID userId, ApprovalStep step, Instant now) {
        for (UUID actor : actors(userId)) {
            if (actor.equals(step.getAssigneeUserId())) {
                return Optional.of(actor);
            }
            if (step.getRoleCode() != null) {
                // roleCode có thể là danh sách cách nhau bằng dấu phẩy: người giữ BẤT KỲ vai trò nào đều duyệt được
                Set<String> have = directory.roleCodesOf(actor);
                for (String role : step.getRoleCode().split(",")) {
                    if (have.contains(role.trim())) {
                        return Optional.of(actor);
                    }
                }
            }
        }
        return Optional.empty();
    }

    // ---------- Cấu hình luồng ----------

    @Transactional(readOnly = true)
    public List<ApprovalFlowDef> listFlows() {
        return flows.findAll();
    }

    @Transactional
    public ApprovalFlowDef upsertFlow(String code, String module, String entityType, String stepsJson,
                                      boolean active) {
        parseSteps(stepsJson);
        return flows.findByCode(code).map(def -> {
            def.update(module, entityType, stepsJson, active);
            return def;
        }).orElseGet(() -> {
            ApprovalFlowDef def = new ApprovalFlowDef(code, module, entityType, stepsJson);
            def.update(module, entityType, stepsJson, active);
            return flows.save(def);
        });
    }

    List<StepSpec> parseSteps(String json) {
        try {
            List<StepSpec> specs = mapper.readValue(json, new TypeReference<List<StepSpec>>() { });
            if (specs.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION, "Luồng duyệt phải có ít nhất một bước");
            }
            for (StepSpec s : specs) {
                if (s.no() < 1 || (s.roleCode() == null || s.roleCode().isBlank()) && s.userId() == null) {
                    throw new BusinessException(ErrorCode.VALIDATION,
                            "Mỗi bước cần số thứ tự ≥ 1 và vai trò hoặc người duyệt");
                }
            }
            return specs;
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cấu hình bước duyệt không phải JSON hợp lệ");
        }
    }
}
