package vn.pxvb.masterdata.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.masterdata.application.PairingService;
import vn.pxvb.masterdata.application.PairingService.LineInput;
import vn.pxvb.masterdata.contract.MasterdataQueries.PairingLine;

/** Ghép cặp vấn–bao theo ca (FR-MD-003, BR-MD-002). */
@RestController
@RequestMapping("/api/v1/md/machine-pairings")
public class PairingController {

    public record LineRequest(String shiftCode, UUID makerMachineId, UUID packerMachineId, BigDecimal feedRatio) {
        LineInput toInput() {
            return new LineInput(shiftCode, makerMachineId, packerMachineId, feedRatio);
        }
    }

    public record CreateRequest(String code, LocalDate effectiveFrom, String changeReason, List<LineRequest> lines) {
    }

    private final PairingService service;

    public PairingController(PairingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('md.pairing.view')")
    public List<Map<String, Object>> list() {
        return service.list();
    }

    @GetMapping("/resolve")
    @PreAuthorize("hasAuthority('md.pairing.view')")
    public List<PairingLine> resolve(@RequestParam(required = false) LocalDate date, @RequestParam String shift) {
        return service.resolve(date == null ? LocalDate.now() : date, shift.toUpperCase());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('md.pairing.view')")
    public Map<String, Object> get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/check")
    @PreAuthorize("hasAuthority('md.pairing.view')")
    public List<Map<String, Object>> check(@PathVariable UUID id) {
        return service.check(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('md.pairing.update')")
    public Map<String, Object> create(@RequestBody CreateRequest req) {
        List<LineInput> lines = req.lines() == null ? List.of()
                : req.lines().stream().map(LineRequest::toInput).toList();
        return service.create(req.code(), req.effectiveFrom(), req.changeReason(), lines);
    }

    @PostMapping("/{id}/lines")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('md.pairing.update')")
    public Map<String, Object> addLine(@PathVariable UUID id, @RequestBody LineRequest req) {
        return service.addLine(id, req.toInput());
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    @PreAuthorize("hasAuthority('md.pairing.update')")
    public Map<String, Object> removeLine(@PathVariable UUID id, @PathVariable UUID lineId) {
        return service.removeLine(id, lineId);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('md.pairing.approve')")
    public Map<String, Object> activate(@PathVariable UUID id) {
        return service.activate(id);
    }

    @PostMapping("/{id}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('md.pairing.update')")
    public Map<String, Object> cloneVersion(@PathVariable UUID id) {
        return service.cloneVersion(id);
    }
}
