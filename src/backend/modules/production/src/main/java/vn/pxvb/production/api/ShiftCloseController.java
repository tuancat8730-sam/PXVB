package vn.pxvb.production.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.production.application.ShiftCloseService;
import vn.pxvb.workflow.domain.Signature;

/** Chốt ca CC-11 và báo cáo ca (FR-CC-080..083, FR-PR-012). */
@RestController
@RequestMapping("/api/v1/pr/sessions/{id}")
public class ShiftCloseController {

    public record ExplainRequest(String checkCode, String subjectKey, String reasonCode, String note) {
    }

    public record DecisionRequest(boolean approve) {
    }

    /** {@code method} PASSWORD | PIN: xác nhận điện tử của người gửi chốt. */
    public record CloseRequest(Signature.Method method, String secret, String note) {
    }

    private final ShiftCloseService service;

    public ShiftCloseController(ShiftCloseService service) {
        this.service = service;
    }

    @GetMapping("/close-checks")
    @PreAuthorize("hasAuthority('pr.close.view')")
    public Map<String, Object> checks(@PathVariable UUID id) {
        return service.checks(id);
    }

    @PostMapping("/explanations")
    @PreAuthorize("hasAuthority('pr.close.explain')")
    public Map<String, Object> explain(@PathVariable UUID id, @RequestBody ExplainRequest req) {
        return service.explain(id, req.checkCode(), req.subjectKey(), req.reasonCode(), req.note());
    }

    @PostMapping("/explanations/{explanationId}/decision")
    @PreAuthorize("hasAuthority('pr.close.approveExplanation')")
    public Map<String, Object> decide(@PathVariable UUID id, @PathVariable UUID explanationId,
                                      @RequestBody DecisionRequest req) {
        return service.decideExplanation(id, explanationId, req.approve());
    }

    @PostMapping("/close-request")
    @PreAuthorize("hasAuthority('pr.close.request')")
    public Map<String, Object> closeRequest(@PathVariable UUID id, @RequestBody CloseRequest req) {
        return service.requestClose(id, req.method(), req.secret(), req.note());
    }

    @GetMapping("/report")
    @PreAuthorize("hasAuthority('pr.session.view')")
    public JsonNode report(@PathVariable UUID id) {
        return service.report(id);
    }
}
