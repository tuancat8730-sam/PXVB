package vn.pxvb.planning.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import vn.pxvb.planning.application.RosterService;
import vn.pxvb.planning.application.RosterService.AssignmentInput;

/** Bố trí nhân sự theo ca (FR-PL-005). */
@RestController
@RequestMapping("/api/v1/pl/rosters")
public class RosterController {

    public record CreateRequest(LocalDate businessDate, String shiftCode, UUID leaderEmployeeId) {
    }

    public record AssignmentRequest(UUID machineId, String position, UUID employeeId) {
        AssignmentInput toInput() {
            return new AssignmentInput(machineId, position, employeeId);
        }
    }

    public record AssignmentsRequest(List<AssignmentRequest> assignments) {
    }

    private final RosterService service;

    public RosterController(RosterService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('pl.roster.view')")
    public List<Map<String, Object>> list(@RequestParam(required = false) LocalDate date,
                                          @RequestParam(required = false) String shift) {
        return service.list(date, shift);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('pl.roster.update')")
    public Map<String, Object> create(@RequestBody CreateRequest req) {
        return service.create(req.businessDate(), req.shiftCode(), req.leaderEmployeeId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pl.roster.view')")
    public Map<String, Object> get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}/assignments")
    @PreAuthorize("hasAuthority('pl.roster.update')")
    public Map<String, Object> replaceAssignments(@PathVariable UUID id, @RequestBody AssignmentsRequest req) {
        List<AssignmentInput> inputs = req.assignments() == null ? List.of()
                : req.assignments().stream().map(AssignmentRequest::toInput).toList();
        return service.replaceAssignments(id, inputs);
    }

    @PostMapping("/{id}/copy-from/{sourceId}")
    @PreAuthorize("hasAuthority('pl.roster.update')")
    public Map<String, Object> copyFrom(@PathVariable UUID id, @PathVariable UUID sourceId) {
        return service.copyFrom(id, sourceId);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('pl.roster.update')")
    public Map<String, Object> publish(@PathVariable UUID id) {
        return service.publish(id);
    }

    @GetMapping("/{id}/export")
    @PreAuthorize("hasAuthority('pl.roster.view')")
    public ResponseEntity<byte[]> export(@PathVariable UUID id) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("bo-tri-nhan-su.xlsx").build().toString())
                .body(service.export(id));
    }
}
