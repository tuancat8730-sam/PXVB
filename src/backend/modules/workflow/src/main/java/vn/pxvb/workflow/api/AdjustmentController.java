package vn.pxvb.workflow.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.common.web.PageResponse;
import vn.pxvb.workflow.application.AdjustmentService;
import vn.pxvb.workflow.domain.AdjustmentRequest;

/** Yêu cầu điều chỉnh dữ liệu sau khóa (CC-03, FR-CC-011..014). */
@RestController
@RequestMapping("/api/v1/workflow/adjustments")
public class AdjustmentController {

    private static final int MAX_PAGE_SIZE = 100;

    public record AdjustmentDto(UUID id, String code, String module, String entityType, UUID entityId,
                                String fieldPath, String oldValue, String newValue, String reason, String status,
                                UUID requestedBy, String approverRoleCode, UUID approvedBy, Instant decidedAt,
                                String decisionNote, Instant createdAt) {
        static AdjustmentDto from(AdjustmentRequest r) {
            return new AdjustmentDto(r.getId(), r.getCode(), r.getModule(), r.getEntityType(), r.getEntityId(),
                    r.getFieldPath(), r.getOldValue(), r.getNewValue(), r.getReason(), r.getStatus().name(),
                    r.getRequestedBy(), r.getApproverRoleCode(), r.getApprovedBy(), r.getDecidedAt(),
                    r.getDecisionNote(), r.getCreatedAt());
        }
    }

    public record CreateRequest(@NotBlank(message = "Vui lòng chọn phân hệ") String module,
                                @NotBlank(message = "Vui lòng chọn loại đối tượng") String entityType,
                                @NotNull(message = "Thiếu đối tượng cần điều chỉnh") UUID entityId,
                                @NotBlank(message = "Vui lòng chọn trường cần điều chỉnh") String fieldPath,
                                String newValue,
                                @NotBlank(message = "Vui lòng nhập lý do điều chỉnh") String reason,
                                String attachmentIds) {
    }

    public record DecisionRequest(String note) {
    }

    private final AdjustmentService service;
    private final CurrentUser currentUser;

    public AdjustmentController(AdjustmentService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('workflow.adjustment.request')")
    public AdjustmentDto create(@Valid @RequestBody CreateRequest req) {
        return AdjustmentDto.from(service.create(req.module(), req.entityType(), req.entityId(), req.fieldPath(),
                req.newValue(), req.reason(), req.attachmentIds(), currentUser.require().userId()));
    }

    /** {@code scope=mine} (mặc định) hoặc {@code scope=pending} (chờ tôi duyệt). */
    @GetMapping
    @PreAuthorize("hasAuthority('workflow.adjustment.view')")
    public PageResponse<AdjustmentDto> list(@RequestParam(defaultValue = "mine") String scope,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        var userId = currentUser.require().userId();
        var result = "pending".equals(scope) ? service.awaitingMyApproval(userId, pageable)
                : service.mine(userId, pageable);
        return PageResponse.of(result, AdjustmentDto::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('workflow.adjustment.view')")
    public AdjustmentDto get(@PathVariable UUID id) {
        return AdjustmentDto.from(service.get(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('workflow.adjustment.approve')")
    public AdjustmentDto approve(@PathVariable UUID id, @RequestBody(required = false) DecisionRequest req) {
        return AdjustmentDto.from(service.approve(id, currentUser.require().userId(),
                req == null ? null : req.note()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('workflow.adjustment.approve')")
    public AdjustmentDto reject(@PathVariable UUID id, @RequestBody(required = false) DecisionRequest req) {
        return AdjustmentDto.from(service.reject(id, currentUser.require().userId(),
                req == null ? null : req.note()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('workflow.adjustment.request')")
    public AdjustmentDto cancel(@PathVariable UUID id) {
        return AdjustmentDto.from(service.cancel(id, currentUser.require().userId()));
    }
}
