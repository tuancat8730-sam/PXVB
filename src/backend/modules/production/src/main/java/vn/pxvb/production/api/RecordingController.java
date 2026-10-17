package vn.pxvb.production.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.production.application.RecordingService;
import vn.pxvb.production.application.RecordingService.Actor;
import vn.pxvb.production.application.RecordingService.ChangeInput;
import vn.pxvb.production.application.RecordingService.OutputInput;
import vn.pxvb.production.application.RecordingService.StopInput;

/** Ghi nhận sản lượng, dừng máy, đổi mác tại máy (FR-PR-002/003/004/005). */
@RestController
@RequestMapping("/api/v1/pr")
public class RecordingController {

    public record OutputRequest(UUID sessionId, Long qty, Long counterReading, Instant periodFrom, Instant periodTo,
                                String note, Instant occurredAt, UUID onBehalfOf) {
    }

    public record StopRequest(UUID sessionId, String reasonCode, Instant startAt, Instant endAt, String note,
                              UUID onBehalfOf) {
    }

    public record EndStopRequest(UUID sessionId, Instant endAt, UUID onBehalfOf) {
    }

    public record ChangeRequest(UUID sessionId, UUID toProductId, Instant changedAt, boolean startStopA,
                                UUID onBehalfOf) {
    }

    public record TechConfirmRequest(String techCauseCode) {
    }

    private final RecordingService service;
    private final CurrentUser currentUser;

    public RecordingController(RecordingService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/machines/{machineId}/output")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('pr.output.record')")
    public Map<String, Object> output(@PathVariable UUID machineId, @RequestBody OutputRequest req,
                                      Authentication auth) {
        return service.recordOutput(new OutputInput(null, machineId, req.sessionId(), req.qty(),
                req.counterReading(), req.periodFrom(), req.periodTo(), req.note(), req.occurredAt()),
                actor(req.onBehalfOf(), auth));
    }

    @PostMapping("/machines/{machineId}/stops")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('pr.stop.record')")
    public Map<String, Object> startStop(@PathVariable UUID machineId, @RequestBody StopRequest req,
                                         Authentication auth) {
        return service.startStop(new StopInput(null, machineId, req.sessionId(), req.reasonCode(), req.startAt(),
                req.endAt(), req.note()), actor(req.onBehalfOf(), auth));
    }

    @PostMapping("/machines/{machineId}/stops/end")
    @PreAuthorize("hasAuthority('pr.stop.record')")
    public Map<String, Object> endStop(@PathVariable UUID machineId, @RequestBody EndStopRequest req,
                                       Authentication auth) {
        return service.endStop(machineId, req.sessionId(), req.endAt(), actor(req.onBehalfOf(), auth));
    }

    @PostMapping("/machines/{machineId}/product-change")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('pr.productChange.record')")
    public Map<String, Object> change(@PathVariable UUID machineId, @RequestBody ChangeRequest req,
                                      Authentication auth) {
        return service.changeProduct(new ChangeInput(null, machineId, req.sessionId(), req.toProductId(),
                req.changedAt(), req.startStopA()), actor(req.onBehalfOf(), auth));
    }

    @PostMapping("/stops/{stopId}/confirm-tech")
    @PreAuthorize("hasAuthority('pr.stop.confirmTech')")
    public Map<String, Object> confirmTech(@PathVariable UUID stopId, @RequestBody TechConfirmRequest req) {
        return service.confirmTech(stopId, req.techCauseCode(), new Actor(currentUser.require().userId(),
                currentUser.deviceId().orElse(null), null));
    }

    /** FR-PR-010: nhập hộ cần quyền riêng; ghi {@code EnteredBy} + {@code OnBehalfOf}. */
    private Actor actor(UUID onBehalfOf, Authentication auth) {
        if (onBehalfOf != null && auth.getAuthorities().stream()
                .noneMatch(a -> "pr.output.onBehalf".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Không có quyền nhập hộ máy khác");
        }
        return new Actor(currentUser.require().userId(), currentUser.deviceId().orElse(null), onBehalfOf);
    }
}
