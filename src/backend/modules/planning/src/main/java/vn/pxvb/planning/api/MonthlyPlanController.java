package vn.pxvb.planning.api;

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
import vn.pxvb.planning.application.MonthlyPlanService;
import vn.pxvb.planning.application.MonthlyPlanService.ItemInput;

/** KHSX tháng (FR-PL-001). Nhập hàng loạt bằng Excel: mẫu {@code monthly-plan} ở {@code /api/v1/dataio}. */
@RestController
@RequestMapping("/api/v1/pl/monthly-plans")
public class MonthlyPlanController {

    public record ItemRequest(UUID productId, String productCode, long plannedPacks, String note) {
        ItemInput toInput() {
            return new ItemInput(productId, productCode, plannedPacks, note);
        }
    }

    public record CreateRequest(int year, int month, List<ItemRequest> items, String sourceNote) {
    }

    public record ReviseRequest(List<ItemRequest> items, String note) {
    }

    private final MonthlyPlanService service;

    public MonthlyPlanController(MonthlyPlanService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('pl.monthlyPlan.view')")
    public List<Map<String, Object>> list(@RequestParam(required = false) Integer year,
                                          @RequestParam(required = false) Integer month) {
        return service.list(year, month);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('pl.monthlyPlan.update')")
    public Map<String, Object> create(@RequestBody CreateRequest req) {
        return service.create(req.year(), req.month(), items(req.items()), req.sourceNote());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pl.monthlyPlan.view')")
    public Map<String, Object> get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/revise")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('pl.monthlyPlan.update')")
    public Map<String, Object> revise(@PathVariable UUID id, @RequestBody ReviseRequest req) {
        return service.revise(id, items(req.items()), req.note());
    }

    @GetMapping("/{id}/progress")
    @PreAuthorize("hasAuthority('pl.monthlyPlan.view')")
    public Map<String, Object> progress(@PathVariable UUID id) {
        return service.progress(id);
    }

    private static List<ItemInput> items(List<ItemRequest> items) {
        return items == null ? List.of() : items.stream().map(ItemRequest::toInput).toList();
    }
}
