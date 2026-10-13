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
import vn.pxvb.planning.application.WeeklyPlanService;
import vn.pxvb.planning.application.WeeklyPlanService.ItemInput;
import vn.pxvb.workflow.application.ApprovalService.Decision;

/** KHSX tuần (FR-PL-002): soạn lưới SP × ngày, xem ngữ cảnh, gửi duyệt, duyệt/từ chối, điều chỉnh (revision). */
@RestController
@RequestMapping("/api/v1/pl/weekly-plans")
public class WeeklyPlanController {

    public record ItemRequest(UUID productId, String productCode, LocalDate planDate, long plannedPacks) {
        ItemInput toInput() {
            return new ItemInput(productId, productCode, planDate, plannedPacks);
        }
    }

    public record CreateRequest(LocalDate weekStart, List<ItemRequest> items, UUID monthlyPlanId, String note) {
    }

    public record ItemsRequest(List<ItemRequest> items, String note) {
    }

    public record SubmitRequest(String varianceReason) {
    }

    public record DecisionRequest(String note) {
    }

    private final WeeklyPlanService service;
    private final CurrentUser currentUser;

    public WeeklyPlanController(WeeklyPlanService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('pl.weeklyPlan.view')")
    public List<Map<String, Object>> list(@RequestParam(required = false) LocalDate weekStart) {
        return service.list(weekStart);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('pl.weeklyPlan.update')")
    public Map<String, Object> create(@RequestBody CreateRequest req) {
        return service.create(req.weekStart(), items(req.items()), req.monthlyPlanId(), req.note());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pl.weeklyPlan.view')")
    public Map<String, Object> get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('pl.weeklyPlan.update')")
    public Map<String, Object> replaceItems(@PathVariable UUID id, @RequestBody ItemsRequest req) {
        return service.replaceItems(id, items(req.items()), req.note());
    }

    @GetMapping("/{id}/context")
    @PreAuthorize("hasAuthority('pl.weeklyPlan.view')")
    public Map<String, Object> context(@PathVariable UUID id) {
        return service.context(id);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('pl.weeklyPlan.submit')")
    public Map<String, Object> submit(@PathVariable UUID id, @RequestBody(required = false) SubmitRequest req) {
        return service.submit(id, req == null ? null : req.varianceReason(), currentUser.require().userId());
    }

    /** Duyệt (Phó Quản đốc theo luồng PL_WEEKLY_APPROVAL; người gửi không được tự duyệt). */
    @PostMapping("/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> approve(@PathVariable UUID id, @RequestBody(required = false) DecisionRequest req) {
        return service.decide(id, currentUser.require().userId(), Decision.APPROVE, note(req));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> reject(@PathVariable UUID id, @RequestBody(required = false) DecisionRequest req) {
        return service.decide(id, currentUser.require().userId(), Decision.REJECT, note(req));
    }

    @PostMapping("/{id}/revise")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('pl.weeklyPlan.update')")
    public Map<String, Object> revise(@PathVariable UUID id) {
        return service.revise(id);
    }

    private static List<ItemInput> items(List<ItemRequest> items) {
        return items == null ? List.of() : items.stream().map(ItemRequest::toInput).toList();
    }

    private static String note(DecisionRequest req) {
        return req == null ? null : req.note();
    }
}
