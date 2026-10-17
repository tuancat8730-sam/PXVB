package vn.pxvb.production.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.production.application.OeeService;

/** OEE và công thức OEE cấu hình (FR-PR-008, SDD §8.3). */
@RestController
@RequestMapping("/api/v1/pr")
public class OeeController {

    public record FormulaRequest(String plannedTimeMode, String qualityMode, boolean capPerformanceAt100,
                                 String note) {
    }

    private final OeeService service;

    public OeeController(OeeService service) {
        this.service = service;
    }

    @GetMapping("/oee")
    @PreAuthorize("hasAuthority('pr.oee.view')")
    public List<Map<String, Object>> oee(@RequestParam(required = false) String grain,
                                         @RequestParam(required = false) LocalDate from,
                                         @RequestParam(required = false) LocalDate to,
                                         @RequestParam(required = false) UUID machineId,
                                         @RequestParam(required = false) String model) {
        return service.query(grain, from, to, machineId, model);
    }

    /** OEE tạm tính của phiên ca: tính lại tại thời điểm gọi. */
    @GetMapping("/sessions/{id}/oee")
    @PreAuthorize("hasAuthority('pr.oee.view')")
    public List<Map<String, Object>> sessionOee(@PathVariable UUID id) {
        return service.computeSession(id);
    }

    @GetMapping("/oee-formula")
    @PreAuthorize("hasAuthority('pr.oee.view')")
    public Map<String, Object> formula() {
        return service.formulaView();
    }

    @PutMapping("/oee-formula")
    @PreAuthorize("hasAuthority('pr.oee.configure')")
    public Map<String, Object> updateFormula(@RequestBody FormulaRequest req) {
        return service.updateFormula(req.plannedTimeMode(), req.qualityMode(), req.capPerformanceAt100(),
                req.note());
    }
}
