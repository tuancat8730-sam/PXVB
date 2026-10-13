package vn.pxvb.planning.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.common.security.PxvbPrincipal;
import vn.pxvb.planning.application.OrderService;
import vn.pxvb.planning.application.OrderService.OrderInput;
import vn.pxvb.planning.application.OrderService.SuggestResult;
import vn.pxvb.workflow.domain.Signature;

/** Lệnh sản xuất ngày/ca (FR-PL-003/004/006). */
@RestController
@RequestMapping("/api/v1/pl/orders")
public class OrderController {

    public record SuggestRequest(UUID weeklyPlanId, LocalDate from, LocalDate to) {
    }

    public record CreateRequest(LocalDate businessDate, String shiftCode, UUID makerMachineId,
                                UUID packerMachineId, UUID productId, long plannedPacks) {
    }

    public record UpdateRequest(UUID makerMachineId, UUID packerMachineId, UUID productId, Long plannedPacks) {
    }

    /** {@code method} PASSWORD | PIN: xác nhận điện tử bằng cách nhập lại mật khẩu/PIN (FR-CC-031). */
    public record ConfirmRequest(Signature.Method method, String secret) {
    }

    public record BatchConfirmRequest(List<UUID> ids, Signature.Method method, String secret) {
    }

    public record AdjustRequest(String type, String reasonCode, String note, UUID newProductId,
                                Long newPlannedPacks) {
    }

    public record CancelRequest(String reason) {
    }

    private final OrderService service;
    private final CurrentUser currentUser;

    public OrderController(OrderService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('pl.order.view')")
    public List<Map<String, Object>> list(@RequestParam(required = false) LocalDate date,
                                          @RequestParam(required = false) String shift,
                                          @RequestParam(required = false) UUID machineId,
                                          @RequestParam(required = false) String status) {
        return service.list(date, shift, machineId, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pl.order.view')")
    public Map<String, Object> get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/suggest")
    @PreAuthorize("hasAuthority('pl.order.suggest')")
    public SuggestResult suggest(@RequestBody SuggestRequest req) {
        return service.suggest(req.weeklyPlanId(), req.from(), req.to());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('pl.order.update')")
    public Map<String, Object> create(@RequestBody CreateRequest req) {
        return service.createManual(new OrderInput(req.businessDate(), req.shiftCode(), req.makerMachineId(),
                req.packerMachineId(), req.productId(), req.plannedPacks()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('pl.order.update')")
    public Map<String, Object> update(@PathVariable UUID id, @RequestBody UpdateRequest req) {
        return service.update(id, req.makerMachineId(), req.packerMachineId(), req.productId(), req.plannedPacks());
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('pl.order.confirm')")
    public Map<String, Object> confirm(@PathVariable UUID id, @RequestBody ConfirmRequest req) {
        PxvbPrincipal p = currentUser.require();
        return service.confirm(id, p.userId(), p.deviceId(), method(req.method()), req.secret());
    }

    @PostMapping("/batch-confirm")
    @PreAuthorize("hasAuthority('pl.order.confirm')")
    public List<Map<String, Object>> batchConfirm(@RequestBody BatchConfirmRequest req) {
        PxvbPrincipal p = currentUser.require();
        return service.batchConfirm(req.ids(), p.userId(), p.deviceId(), method(req.method()), req.secret());
    }

    @PostMapping("/{id}/adjustments")
    @PreAuthorize("hasAuthority('pl.order.update')")
    public Map<String, Object> adjust(@PathVariable UUID id, @RequestBody AdjustRequest req) {
        return service.adjust(id, req.type(), req.reasonCode(), req.note(), req.newProductId(),
                req.newPlannedPacks(), currentUser.require().userId());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('pl.order.update')")
    public Map<String, Object> cancel(@PathVariable UUID id, @RequestBody CancelRequest req) {
        return service.cancel(id, req.reason());
    }

    private static Signature.Method method(Signature.Method m) {
        return m == null ? Signature.Method.PASSWORD : m;
    }
}
