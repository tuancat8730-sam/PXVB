package vn.pxvb.material.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.material.application.ConsumptionService;

/** Theo dõi, giải trình, chốt tiêu hao ca và báo cáo (FR-MT-006..009). */
@RestController
@RequestMapping("/api/v1/mt")
public class ConsumptionController {

    public record ExplainRequest(String explanation) {
    }

    private final ConsumptionService service;

    public ConsumptionController(ConsumptionService service) {
        this.service = service;
    }

    /** Tiêu hao thời gian thực: tính lại tại thời điểm gọi (ca đang mở hoặc đang chờ chốt). */
    @GetMapping("/sessions/{sessionId}/consumption")
    @PreAuthorize("hasAuthority('mt.consumption.view')")
    public List<Map<String, Object>> live(@PathVariable UUID sessionId) {
        return service.compute(sessionId);
    }

    @PostMapping("/consumption/{id}/explain")
    @PreAuthorize("hasAuthority('mt.consumption.explain')")
    public Map<String, Object> explain(@PathVariable UUID id, @RequestBody ExplainRequest req) {
        return service.explain(id, req.explanation());
    }

    @PostMapping("/sessions/{sessionId}/consumption/confirm")
    @PreAuthorize("hasAuthority('mt.consumption.confirm')")
    public List<Map<String, Object>> confirm(@PathVariable UUID sessionId) {
        return service.confirm(sessionId);
    }

    @GetMapping("/reports/shift/{sessionId}")
    @PreAuthorize("hasAuthority('mt.consumption.view')")
    public Map<String, Object> report(@PathVariable UUID sessionId) {
        return service.report(sessionId);
    }
}
