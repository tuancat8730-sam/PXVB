package vn.pxvb.material.api;

import java.math.BigDecimal;
import java.time.Instant;
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
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.material.application.IssueService;
import vn.pxvb.material.application.IssueService.Actor;
import vn.pxvb.material.application.IssueService.CountInput;
import vn.pxvb.material.application.IssueService.IssueInput;
import vn.pxvb.material.application.IssueService.ReceiptInput;
import vn.pxvb.material.application.IssueService.ReturnInput;

/** Cấp phát thực tế, nhận, hoàn trả, kiểm đếm (FR-MT-003/004/005). */
@RestController
@RequestMapping("/api/v1/mt")
public class IssueController {

    public record IssueRequest(UUID slipLineId, LocalDate businessDate, String shiftCode, Integer slotNo,
                               UUID machineId, UUID materialId, String lotNo, BigDecimal issuedQty,
                               String reasonCode, String note, Instant occurredAt) {
    }

    public record ReceiptRequest(UUID issueRecordId, BigDecimal receivedQty, BigDecimal remainingBefore,
                                 String note, Instant occurredAt) {
    }

    public record ResolveRequest(String note) {
    }

    public record ReturnRequest(LocalDate businessDate, String shiftCode, UUID machineId, UUID materialId,
                                BigDecimal qty, String reason, Instant occurredAt) {
    }

    public record CountRequest(LocalDate businessDate, String shiftCode, UUID machineId, UUID materialId,
                               String kind, BigDecimal qty, String note, Instant occurredAt) {
    }

    private final IssueService service;
    private final CurrentUser currentUser;

    public IssueController(IssueService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    private Actor actor() {
        return new Actor(currentUser.require().userId(), currentUser.deviceId().orElse(null));
    }

    @PostMapping("/issue-records")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('mt.issue.record')")
    public Map<String, Object> issue(@RequestBody IssueRequest r) {
        return service.recordIssue(new IssueInput(null, r.slipLineId(), r.businessDate(), r.shiftCode(), r.slotNo(),
                r.machineId(), r.materialId(), r.lotNo(), r.issuedQty(), r.reasonCode(), r.note(),
                r.occurredAt()), actor());
    }

    @GetMapping("/issue-records")
    @PreAuthorize("hasAuthority('mt.slip.view')")
    public List<Map<String, Object>> issues(@RequestParam LocalDate date,
                                            @RequestParam(required = false) String shift,
                                            @RequestParam(required = false) UUID machineId) {
        return service.issues(date, shift, machineId);
    }

    @PostMapping("/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('mt.receipt.confirm')")
    public Map<String, Object> receipt(@RequestBody ReceiptRequest r) {
        return service.confirmReceipt(new ReceiptInput(null, r.issueRecordId(), r.receivedQty(),
                r.remainingBefore(), r.note(), r.occurredAt()), actor());
    }

    @GetMapping("/receipts")
    @PreAuthorize("hasAuthority('mt.slip.view')")
    public List<Map<String, Object>> receipts(@RequestParam LocalDate date,
                                              @RequestParam(required = false) String shift,
                                              @RequestParam(required = false) String status) {
        return service.receipts(date, shift, status);
    }

    @PostMapping("/receipts/{id}/resolve")
    @PreAuthorize("hasAuthority('mt.receipt.resolve')")
    public Map<String, Object> resolve(@PathVariable UUID id, @RequestBody ResolveRequest r) {
        return service.resolveReceipt(id, r.note());
    }

    @PostMapping("/returns")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('mt.return.record')")
    public Map<String, Object> returns(@RequestBody ReturnRequest r) {
        return service.recordReturn(new ReturnInput(null, r.businessDate(), r.shiftCode(), r.machineId(),
                r.materialId(), r.qty(), r.reason(), r.occurredAt()), actor());
    }

    @PostMapping("/stock-counts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('mt.count.record')")
    public Map<String, Object> count(@RequestBody CountRequest r) {
        return service.recordCount(new CountInput(null, r.businessDate(), r.shiftCode(), r.machineId(),
                r.materialId(), r.kind(), r.qty(), r.note(), r.occurredAt()), actor());
    }
}
