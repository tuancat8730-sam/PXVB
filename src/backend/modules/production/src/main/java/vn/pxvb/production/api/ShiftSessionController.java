package vn.pxvb.production.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import vn.pxvb.production.application.ProductionQueryService;
import vn.pxvb.production.application.ShiftSessionService;

/** Phiên ca và theo dõi thời gian thực (FR-PR-001/006). */
@RestController
@RequestMapping("/api/v1/pr/sessions")
public class ShiftSessionController {

    public record OpenRequest(LocalDate businessDate, String shiftCode) {
    }

    private final ShiftSessionService sessions;
    private final ProductionQueryService queries;

    public ShiftSessionController(ShiftSessionService sessions, ProductionQueryService queries) {
        this.sessions = sessions;
        this.queries = queries;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('pr.session.view')")
    public List<Map<String, Object>> list(@RequestParam(required = false) LocalDate date,
                                          @RequestParam(required = false) String shift,
                                          @RequestParam(required = false) String status) {
        return sessions.list(date, shift, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pr.session.view')")
    public Map<String, Object> get(@PathVariable UUID id) {
        return sessions.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('pr.session.open')")
    public Map<String, Object> open(@RequestBody OpenRequest req) {
        return sessions.open(req.businessDate(), req.shiftCode());
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasAuthority('pr.session.end')")
    public Map<String, Object> end(@PathVariable UUID id) {
        return sessions.end(id);
    }

    @GetMapping("/{id}/dashboard")
    @PreAuthorize("hasAuthority('pr.output.view')")
    public Map<String, Object> dashboard(@PathVariable UUID id) {
        return queries.dashboard(id);
    }

    @GetMapping("/{id}/outputs")
    @PreAuthorize("hasAuthority('pr.output.view')")
    public List<Map<String, Object>> outputs(@PathVariable UUID id,
                                             @RequestParam(required = false) UUID machineId) {
        return queries.outputs(id, machineId);
    }

    @GetMapping("/{id}/stops")
    @PreAuthorize("hasAuthority('pr.output.view')")
    public List<Map<String, Object>> stops(@PathVariable UUID id, @RequestParam(required = false) UUID machineId) {
        return queries.stops(id, machineId);
    }
}
