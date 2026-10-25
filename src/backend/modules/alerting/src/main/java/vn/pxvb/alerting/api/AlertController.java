package vn.pxvb.alerting.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.alerting.application.AlertService;
import vn.pxvb.alerting.application.AlertService.AlertDetail;
import vn.pxvb.alerting.domain.Alert;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.common.security.DataScope;
import vn.pxvb.common.web.PageResponse;

/** Trung tâm cảnh báo (FR-CC-051, FR-CC-054). */
@RestController
@RequestMapping("/api/v1/alerting/alerts")
public class AlertController {

    private static final int MAX_PAGE_SIZE = 200;

    public record AlertDto(UUID id, String ruleCode, String severity, String module, String subjectType,
                           String subjectId, UUID machineId, UUID shiftSessionId, String title, String message,
                           String status, Instant raisedAt, Instant lastOccurredAt, int occurrenceCount,
                           Instant ackAt, UUID ackBy, Instant resolvedAt, UUID resolvedBy, String cause,
                           String resolutionNote, int escalationLevel) {
        static AlertDto from(Alert a) {
            return new AlertDto(a.getId(), a.getRuleCode(), a.getSeverity().name(), a.getModule(),
                    a.getSubjectType(), a.getSubjectId(), a.getMachineId(), a.getShiftSessionId(), a.getTitle(),
                    a.getMessage(), a.getStatus().name(), a.getRaisedAt(), a.getLastOccurredAt(),
                    a.getOccurrenceCount(), a.getAckAt(), a.getAckBy(), a.getResolvedAt(), a.getResolvedBy(),
                    a.getCause(), a.getResolutionNote(), a.getEscalationLevel());
        }
    }

    public record HistoryDto(String fromStatus, String toStatus, UUID byUserId, Instant at, String note) {
    }

    public record RecipientDto(UUID userId, int escalationLevel, Instant deliveredAt) {
    }

    public record AlertDetailDto(AlertDto alert, List<HistoryDto> history, List<RecipientDto> recipients) {
        static AlertDetailDto from(AlertDetail d) {
            return new AlertDetailDto(AlertDto.from(d.alert()),
                    d.history().stream().map(h -> new HistoryDto(h.getFromStatus(), h.getToStatus(),
                            h.getByUserId(), h.getAt(), h.getNote())).toList(),
                    d.recipients().stream().map(r -> new RecipientDto(r.getUserId(), r.getEscalationLevel(),
                            r.getDeliveredAt())).toList());
        }
    }

    public record NoteRequest(String note) {
    }

    public record ResolveRequest(String cause, String note) {
    }

    private final AlertService service;
    private final CurrentUser currentUser;

    public AlertController(AlertService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('alerting.alert.view')")
    public PageResponse<AlertDto> search(@RequestParam(required = false) String module,
                                         @RequestParam(required = false) Alert.Severity severity,
                                         @RequestParam(required = false) Alert.Status status,
                                         @RequestParam(required = false) UUID machineId,
                                         @RequestParam(required = false) UUID shiftSessionId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int pageSize) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "raisedAt"));
        return PageResponse.of(service.search(module, severity, status, machineId, shiftSessionId, scopes(),
                pageable), AlertDto::from);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('alerting.alert.view')")
    public org.springframework.http.ResponseEntity<byte[]> export(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Alert.Severity severity,
            @RequestParam(required = false) Alert.Status status,
            @RequestParam(required = false) UUID machineId,
            @RequestParam(required = false) UUID shiftSessionId) {
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.attachment().filename("canh-bao.xlsx").build()
                                .toString())
                .body(service.export(module, severity, status, machineId, shiftSessionId, scopes()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('alerting.alert.view')")
    public AlertDetailDto get(@PathVariable UUID id) {
        return AlertDetailDto.from(service.detail(id, scopes()));
    }

    @PostMapping("/{id}/ack")
    @PreAuthorize("hasAuthority('alerting.alert.handle')")
    public AlertDto ack(@PathVariable UUID id, @RequestBody(required = false) NoteRequest req) {
        return AlertDto.from(service.acknowledge(id, currentUser.require().userId(), note(req), scopes()));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('alerting.alert.handle')")
    public AlertDto start(@PathVariable UUID id, @RequestBody(required = false) NoteRequest req) {
        return AlertDto.from(service.startWork(id, currentUser.require().userId(), note(req), scopes()));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('alerting.alert.handle')")
    public AlertDto resolve(@PathVariable UUID id, @RequestBody ResolveRequest req) {
        return AlertDto.from(service.resolve(id, currentUser.require().userId(), req.cause(), req.note(), scopes()));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('alerting.alert.handle')")
    public AlertDto close(@PathVariable UUID id, @RequestBody(required = false) NoteRequest req) {
        return AlertDto.from(service.close(id, currentUser.require().userId(), note(req), scopes()));
    }

    /** Phạm vi dữ liệu của người dùng hiện tại (FR-CC-006); service lọc và từ chối theo phạm vi này. */
    private List<DataScope> scopes() {
        return currentUser.require().scopes();
    }

    private static String note(NoteRequest req) {
        return req == null ? null : req.note();
    }
}
