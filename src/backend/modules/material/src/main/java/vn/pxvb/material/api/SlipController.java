package vn.pxvb.material.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.material.application.SlipService;

/** Phiếu cấp phát (FR-MT-001/002/003). */
@RestController
@RequestMapping("/api/v1/mt")
public class SlipController {

    public record LineRequest(BigDecimal plannedQty, String reason) {
    }

    public record BatchConfirmRequest(List<UUID> ids) {
    }

    private final SlipService service;

    public SlipController(SlipService service) {
        this.service = service;
    }

    @GetMapping("/slips")
    @PreAuthorize("hasAuthority('mt.slip.view')")
    public List<Map<String, Object>> list(@RequestParam(required = false) LocalDate date,
                                          @RequestParam(required = false) String shift,
                                          @RequestParam(required = false) String type,
                                          @RequestParam(required = false) UUID machineId,
                                          @RequestParam(required = false) String status) {
        return service.list(date, shift, type, machineId, status);
    }

    @GetMapping("/slips/{id}")
    @PreAuthorize("hasAuthority('mt.slip.view')")
    public Map<String, Object> get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/slips/{id}/lines/{lineId}")
    @PreAuthorize("hasAuthority('mt.slip.update')")
    public Map<String, Object> updateLine(@PathVariable UUID id, @PathVariable UUID lineId,
                                          @RequestBody LineRequest req) {
        return service.updateLine(id, lineId, req.plannedQty(), req.reason());
    }

    @PostMapping("/slips/{id}/confirm")
    @PreAuthorize("hasAuthority('mt.slip.confirm')")
    public Map<String, Object> confirm(@PathVariable UUID id) {
        return service.confirm(id);
    }

    @PostMapping("/slips/confirm-batch")
    @PreAuthorize("hasAuthority('mt.slip.confirm')")
    public List<Map<String, Object>> confirmBatch(@RequestBody BatchConfirmRequest req) {
        return service.confirmAll(req.ids());
    }

    @PostMapping("/orders/{orderId}/regenerate-slips")
    @PreAuthorize("hasAuthority('mt.slip.update')")
    public Map<String, Object> regenerate(@PathVariable UUID orderId) {
        return Map.of("created", service.regenerate(orderId));
    }

    @GetMapping("/issue-worklist")
    @PreAuthorize("hasAuthority('mt.slip.view')")
    public List<Map<String, Object>> worklist(@RequestParam LocalDate date, @RequestParam String shift,
                                              @RequestParam(required = false) Integer slotNo) {
        return service.worklist(date, shift, slotNo);
    }
}
