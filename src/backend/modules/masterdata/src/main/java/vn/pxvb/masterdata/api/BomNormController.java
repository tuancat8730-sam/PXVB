package vn.pxvb.masterdata.api;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.common.web.PageResponse;
import vn.pxvb.masterdata.application.BomNormService;
import vn.pxvb.masterdata.application.BomNormService.LineInput;
import vn.pxvb.masterdata.contract.MasterdataQueries.Norm;

/** Định mức NVL có phiên bản (FR-MD-007): soạn nháp → gửi duyệt → duyệt (Phó QĐ) → hiệu lực. */
@RestController
@RequestMapping("/api/v1/md/bom-norms")
public class BomNormController {

    public record LineRequest(UUID materialId, String materialCode, BigDecimal qtyPer1000Packs, String uom,
                              String consumerType) {
        LineInput toInput() {
            return new LineInput(materialId, materialCode, qtyPer1000Packs, uom, consumerType);
        }
    }

    public record CreateRequest(@NotNull(message = "Vui lòng chọn sản phẩm") UUID productId,
                                LocalDate effectiveFrom, String changeReason, List<LineRequest> lines) {
    }

    public record LinesRequest(List<LineRequest> lines, LocalDate effectiveFrom, String changeReason) {
    }

    public record RejectRequest(String reason) {
    }

    private final BomNormService service;
    private final CurrentUser currentUser;

    public BomNormController(BomNormService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('md.bomNorm.view')")
    public PageResponse<Map<String, Object>> list(@RequestParam(required = false) UUID productId,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int pageSize) {
        return service.list(productId, status, page, pageSize);
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('md.bomNorm.view')")
    public Norm active(@RequestParam UUID productId, @RequestParam(required = false) LocalDate asOf) {
        return service.active(productId, asOf == null ? LocalDate.now() : asOf).orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "Sản phẩm chưa có định mức hiệu lực"));
    }

    @GetMapping("/compare")
    @PreAuthorize("hasAuthority('md.bomNorm.view')")
    public List<Map<String, Object>> compare(@RequestParam UUID a, @RequestParam UUID b) {
        return service.compare(a, b);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('md.bomNorm.view')")
    public Map<String, Object> get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('md.bomNorm.create')")
    public Map<String, Object> create(@RequestBody CreateRequest req) {
        return service.create(req.productId(), req.effectiveFrom(), req.changeReason(), inputs(req.lines()));
    }

    @PutMapping("/{id}/lines")
    @PreAuthorize("hasAuthority('md.bomNorm.create')")
    public Map<String, Object> replaceLines(@PathVariable UUID id, @RequestBody LinesRequest req) {
        return service.replaceLines(id, inputs(req.lines()), req.effectiveFrom(), req.changeReason());
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('md.bomNorm.create')")
    public Map<String, Object> submit(@PathVariable UUID id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('md.bomNorm.approve')")
    public Map<String, Object> approve(@PathVariable UUID id) {
        boolean canBackdate = currentUser.require().permissions().contains("md.bomNorm.backdate");
        return service.approve(id, canBackdate);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('md.bomNorm.approve')")
    public Map<String, Object> reject(@PathVariable UUID id, @RequestBody(required = false) RejectRequest req) {
        return service.reject(id, req == null ? null : req.reason());
    }

    @PostMapping("/{id}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('md.bomNorm.create')")
    public Map<String, Object> cloneNorm(@PathVariable UUID id) {
        return service.cloneNorm(id);
    }

    private static List<LineInput> inputs(List<LineRequest> lines) {
        return lines == null ? List.of() : lines.stream().map(LineRequest::toInput).toList();
    }
}
