package vn.pxvb.planning.application;

import com.github.f4b6a3.uuid.UuidCreator;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.audit.AuditLogWriter;
import vn.pxvb.common.audit.AuditLogWriter.FieldChange;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.masterdata.contract.MasterdataQueries;
import vn.pxvb.masterdata.contract.MasterdataQueries.ProductInfo;
import vn.pxvb.workflow.application.ApprovalService;
import vn.pxvb.workflow.application.ApprovalService.ApprovalView;
import vn.pxvb.workflow.contract.WorkflowEvents.ApprovalCompleted;
import vn.pxvb.workflow.domain.ApprovalInstance;
import vn.pxvb.workflow.domain.ApprovalStep;

/**
 * KHSX tuần (FR-PL-002, BR-PL-001): lưới SP × ngày; xem còn lại của tháng; vượt ngưỡng {@code PL_WEEK_VS_MONTH_PCT}
 * bắt buộc nhập lý do khi gửi duyệt; duyệt qua luồng {@code PL_WEEKLY_APPROVAL}; điều chỉnh sau duyệt = revision mới.
 */
@Service
public class WeeklyPlanService {

    public record ItemInput(UUID productId, String productCode, LocalDate planDate, long plannedPacks) {
    }

    private static final String FLOW = "PL_WEEKLY_APPROVAL";
    private static final String ENTITY_TYPE = "WeeklyPlan";
    private static final BigDecimal DEFAULT_TOLERANCE_PCT = BigDecimal.TEN;

    private final JdbcTemplate jdbc;
    private final MasterdataQueries masterdata;
    private final MonthlyPlanService monthly;
    private final ApprovalService approvals;
    private final AuditLogWriter audit;
    private final CurrentUser currentUser;

    public WeeklyPlanService(JdbcTemplate jdbc, MasterdataQueries masterdata, MonthlyPlanService monthly,
                             ApprovalService approvals, AuditLogWriter audit, CurrentUser currentUser) {
        this.jdbc = jdbc;
        this.masterdata = masterdata;
        this.monthly = monthly;
        this.approvals = approvals;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    public static LocalDate weekStartOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    // ---------- Soạn thảo ----------

    @Transactional
    public Map<String, Object> create(LocalDate anyDateInWeek, List<ItemInput> items, UUID monthlyPlanId,
                                      String note) {
        LocalDate weekStart = weekStartOf(anyDateInWeek);
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(revision), 0) FROM pl_weekly_plan "
                + "WHERE week_start = ?", Integer.class, weekStart);
        return insert(weekStart, (max == null ? 0 : max) + 1, items, monthlyPlanId, note);
    }

    private Map<String, Object> insert(LocalDate weekStart, int revision, List<ItemInput> items, UUID monthlyPlanId,
                                       String note) {
        UUID id = UuidCreator.getTimeOrderedEpoch();
        String code = "KHW-%s-R%d".formatted(weekStart.toString().replace("-", ""), revision);
        String user = user();
        jdbc.update("INSERT INTO pl_weekly_plan(id, row_version, created_at, created_by, updated_at, updated_by, "
                        + "code, week_start, revision, status, monthly_plan_id, note) VALUES (UUID_TO_BIN(?), 0, "
                        + "UTC_TIMESTAMP(6), UUID_TO_BIN(?), UTC_TIMESTAMP(6), UUID_TO_BIN(?), ?, ?, ?, 'DRAFT', "
                        + "UUID_TO_BIN(?), ?)", id.toString(), user, user, code, weekStart, revision,
                monthlyPlanId == null ? null : monthlyPlanId.toString(), note);
        writeItems(id, weekStart, items);
        audit(id, "CREATE", List.of(new FieldChange("code", null, code)));
        return get(id);
    }

    @Transactional
    public Map<String, Object> replaceItems(UUID id, List<ItemInput> items, String note) {
        Map<String, Object> plan = get(id);
        requireStatus(plan, "DRAFT");
        jdbc.update("DELETE FROM pl_weekly_plan_item WHERE weekly_plan_id = UUID_TO_BIN(?)", id.toString());
        writeItems(id, ((java.sql.Date) plan.get("weekStart")).toLocalDate(), items);
        jdbc.update("UPDATE pl_weekly_plan SET note = COALESCE(?, note), row_version = row_version + 1, "
                + "updated_at = UTC_TIMESTAMP(6), updated_by = UUID_TO_BIN(?) WHERE id = UUID_TO_BIN(?)",
                note, user(), id.toString());
        audit(id, "UPDATE_ITEMS", List.of(new FieldChange("items", null, items.size())));
        return get(id);
    }

    @Transactional
    public Map<String, Object> revise(UUID id) {
        Map<String, Object> source = get(id);
        if ("DRAFT".equals(source.get("status"))) {
            throw new BusinessException(ErrorCode.VALIDATION, "Bản nháp chưa cần tạo revision, hãy sửa trực tiếp");
        }
        LocalDate weekStart = ((java.sql.Date) source.get("weekStart")).toLocalDate();
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(revision), 0) FROM pl_weekly_plan "
                + "WHERE week_start = ?", Integer.class, weekStart);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) source.get("items");
        List<ItemInput> items = rows.stream().map(r -> new ItemInput(UUID.fromString((String) r.get("productId")),
                null, ((java.sql.Date) r.get("planDate")).toLocalDate(),
                ((Number) r.get("plannedPacks")).longValue())).toList();
        String monthlyId = (String) source.get("monthlyPlanId");
        return insert(weekStart, (max == null ? 0 : max) + 1, items,
                monthlyId == null ? null : UUID.fromString(monthlyId), "Điều chỉnh từ " + source.get("code"));
    }

    // ---------- Truy vấn ----------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(LocalDate weekStart) {
        String sql = "SELECT BIN_TO_UUID(id) AS id, code, week_start AS weekStart, revision, status, "
                + "variance_reason AS varianceReason FROM pl_weekly_plan";
        return weekStart == null
                ? jdbc.queryForList(sql + " ORDER BY week_start DESC, revision DESC LIMIT 200")
                : jdbc.queryForList(sql + " WHERE week_start = ? ORDER BY revision DESC", weekStartOf(weekStart));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, code, week_start AS "
                + "weekStart, revision, status, BIN_TO_UUID(monthly_plan_id) AS monthlyPlanId, BIN_TO_UUID("
                + "submitted_by) AS submittedBy, BIN_TO_UUID(approved_by) AS approvedBy, variance_reason AS "
                + "varianceReason, note, row_version AS rowVersion FROM pl_weekly_plan WHERE id = UUID_TO_BIN(?)",
                id.toString());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy KHSX tuần");
        }
        Map<String, Object> view = new LinkedHashMap<>(rows.get(0));
        List<Map<String, Object>> items = jdbc.queryForList("SELECT BIN_TO_UUID(product_id) AS productId, "
                + "plan_date AS planDate, planned_packs AS plannedPacks FROM pl_weekly_plan_item "
                + "WHERE weekly_plan_id = UUID_TO_BIN(?) ORDER BY plan_date", id.toString());
        Map<UUID, ProductInfo> products = masterdata.products(items.stream()
                .map(r -> UUID.fromString((String) r.get("productId"))).distinct().toList());
        items.forEach(r -> {
            ProductInfo p = products.get(UUID.fromString((String) r.get("productId")));
            r.put("productCode", p == null ? null : p.code());
        });
        view.put("items", items);
        List<ApprovalView> instances = approvals.forEntity(ENTITY_TYPE, id);
        view.put("approvalInstanceId", instances.isEmpty() ? null : instances.get(0).instance().getId());
        view.put("approvalStatus", instances.isEmpty() ? null : instances.get(0).instance().getStatus().name());
        return view;
    }

    /** Ngữ cảnh lập kế hoạch tuần: còn lại của tháng theo SP; lịch bảo trì & tồn NVL bổ sung khi có PM/MT. */
    @Transactional(readOnly = true)
    public Map<String, Object> context(UUID id) {
        Map<String, Object> plan = get(id);
        LocalDate weekStart = ((java.sql.Date) plan.get("weekStart")).toLocalDate();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) plan.get("items");
        List<Map<String, Object>> products = new ArrayList<>();
        for (Row row : variance(weekStart, items)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", row.productId().toString());
            m.put("year", row.month().getYear());
            m.put("month", row.month().getMonthValue());
            m.put("monthlyPlanned", row.monthlyPlanned());
            m.put("allocatedByOtherWeeks", row.allocatedByOthers());
            m.put("remainingBeforeThisWeek", row.remaining());
            m.put("thisWeek", row.thisWeek());
            m.put("exceedsTolerance", row.exceeds());
            products.add(m);
        }
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("weekStart", weekStart.toString());
        ctx.put("tolerancePct", tolerancePct(weekStart));
        ctx.put("products", products);
        ctx.put("maintenance", List.of());
        ctx.put("materialStock", List.of());
        return ctx;
    }

    // ---------- Duyệt ----------

    @Transactional
    public Map<String, Object> submit(UUID id, String varianceReason, UUID userId) {
        Map<String, Object> plan = get(id);
        requireStatus(plan, "DRAFT");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) plan.get("items");
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION, "KHSX tuần chưa có sản phẩm nào");
        }
        LocalDate weekStart = ((java.sql.Date) plan.get("weekStart")).toLocalDate();
        boolean exceeds = variance(weekStart, items).stream().anyMatch(Row::exceeds);
        if (exceeds && (varianceReason == null || varianceReason.isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION,
                    "Sản lượng tuần vượt phần còn lại của tháng quá ngưỡng, cần nhập lý do khi gửi duyệt",
                    Map.of("varianceReason", List.of("Vui lòng nhập lý do chênh lệch so với KHSX tháng")));
        }
        jdbc.update("UPDATE pl_weekly_plan SET status = 'SUBMITTED', submitted_by = UUID_TO_BIN(?), "
                        + "variance_reason = ?, row_version = row_version + 1, updated_at = UTC_TIMESTAMP(6), "
                        + "updated_by = UUID_TO_BIN(?) WHERE id = UUID_TO_BIN(?)",
                userId.toString(), varianceReason, userId.toString(), id.toString());
        approvals.start(FLOW, id, userId, varianceReason);
        audit(id, "SUBMIT", List.of(new FieldChange("status", "DRAFT", "SUBMITTED")));
        return get(id);
    }

    /** Duyệt/từ chối qua luồng workflow đang chờ của kế hoạch này. */
    @Transactional
    public Map<String, Object> decide(UUID id, UUID userId, ApprovalService.Decision decision, String note) {
        UUID instanceId = approvals.forEntity(ENTITY_TYPE, id).stream()
                .filter(v -> v.instance().getStatus() == ApprovalInstance.Status.PENDING)
                .map(v -> v.instance().getId()).findFirst().orElseThrow(
                        () -> new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                                "KHSX tuần không có yêu cầu duyệt đang chờ"));
        approvals.decide(instanceId, userId, decision, note);
        return get(id);
    }

    /** Đồng bộ trạng thái kế hoạch khi luồng duyệt kết thúc (cùng giao dịch với quyết định duyệt). */
    @EventListener
    @Transactional
    public void onApprovalCompleted(ApprovalCompleted event) {
        if (!ENTITY_TYPE.equals(event.entityType())) {
            return;
        }
        UUID planId = event.entityId();
        // Luồng duyệt dùng chung có thể được khởi tạo cho đối tượng không phải KHSX tuần của module này: bỏ qua.
        List<LocalDate> weeks = jdbc.queryForList("SELECT week_start FROM pl_weekly_plan WHERE id = UUID_TO_BIN(?)",
                LocalDate.class, planId.toString());
        if (weeks.isEmpty()) {
            return;
        }
        if ("APPROVED".equals(event.status())) {
            LocalDate weekStart = weeks.get(0);
            jdbc.update("UPDATE pl_weekly_plan SET status = 'SUPERSEDED', row_version = row_version + 1 "
                    + "WHERE week_start = ? AND status = 'APPROVED' AND id <> UUID_TO_BIN(?)", weekStart,
                    planId.toString());
            UUID approver = approvals.get(event.instanceId()).steps().stream()
                    .filter(s -> s.getStatus() == ApprovalStep.Status.APPROVED).map(ApprovalStep::getDecidedBy)
                    .filter(java.util.Objects::nonNull).findFirst().orElse(null);
            jdbc.update("UPDATE pl_weekly_plan SET status = 'APPROVED', approved_by = UUID_TO_BIN(?), "
                    + "row_version = row_version + 1, updated_at = UTC_TIMESTAMP(6) WHERE id = UUID_TO_BIN(?)",
                    approver == null ? null : approver.toString(), planId.toString());
            audit(planId, "APPROVE", List.of(new FieldChange("status", "SUBMITTED", "APPROVED")));
        } else {
            jdbc.update("UPDATE pl_weekly_plan SET status = 'DRAFT', row_version = row_version + 1, "
                    + "updated_at = UTC_TIMESTAMP(6) WHERE id = UUID_TO_BIN(?) AND status = 'SUBMITTED'",
                    planId.toString());
            audit(planId, event.status(), List.of(new FieldChange("status", "SUBMITTED", "DRAFT")));
        }
    }

    // ---------- Nội bộ ----------

    private record Row(UUID productId, YearMonth month, long monthlyPlanned, long allocatedByOthers, long thisWeek,
                       boolean exceeds) {
        long remaining() {
            return monthlyPlanned - allocatedByOthers;
        }
    }

    private List<Row> variance(LocalDate weekStart, List<Map<String, Object>> items) {
        BigDecimal tolerance = tolerancePct(weekStart);
        Map<String, Long> thisWeek = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            LocalDate date = ((java.sql.Date) item.get("planDate")).toLocalDate();
            String key = item.get("productId") + "|" + YearMonth.from(date);
            thisWeek.merge(key, ((Number) item.get("plannedPacks")).longValue(), Long::sum);
        }
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, Long> e : thisWeek.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            UUID productId = UUID.fromString(parts[0]);
            YearMonth month = YearMonth.parse(parts[1]);
            long planned = monthly.activePlannedByProduct(month.getYear(), month.getMonthValue())
                    .getOrDefault(productId, 0L);
            Long others = jdbc.queryForObject("SELECT COALESCE(SUM(i.planned_packs), 0) FROM pl_weekly_plan_item i "
                    + "JOIN pl_weekly_plan w ON w.id = i.weekly_plan_id WHERE w.status = 'APPROVED' "
                    + "AND w.week_start <> ? AND i.product_id = UUID_TO_BIN(?) AND i.plan_date BETWEEN ? AND ?",
                    Long.class, weekStart, productId.toString(), month.atDay(1), month.atEndOfMonth());
            long allocated = others == null ? 0 : others;
            BigDecimal limit = BigDecimal.valueOf(Math.max(planned - allocated, 0))
                    .multiply(BigDecimal.ONE.add(tolerance.movePointLeft(2)));
            boolean exceeds = BigDecimal.valueOf(e.getValue()).compareTo(limit) > 0;
            rows.add(new Row(productId, month, planned, allocated, e.getValue(), exceeds));
        }
        return rows;
    }

    private BigDecimal tolerancePct(LocalDate asOf) {
        return masterdata.resolveThreshold("PL_WEEK_VS_MONTH_PCT", null, null, null, null, null, asOf)
                .map(MasterdataQueries.ThresholdValue::value).orElse(DEFAULT_TOLERANCE_PCT);
    }

    private void writeItems(UUID planId, LocalDate weekStart, List<ItemInput> items) {
        Set<String> seen = new HashSet<>();
        List<UUID> productIds = new ArrayList<>();
        List<UUID> resolvedIds = new ArrayList<>();
        for (ItemInput in : items) {
            UUID pid = in.productId() != null ? in.productId() : masterdata.productIdByCode(in.productCode())
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION,
                            "Sản phẩm không tồn tại: " + in.productCode()));
            if (in.planDate() == null || in.planDate().isBefore(weekStart) || in.planDate().isAfter(
                    weekStart.plusDays(6))) {
                throw new BusinessException(ErrorCode.VALIDATION, "Ngày kế hoạch phải nằm trong tuần bắt đầu "
                        + weekStart);
            }
            if (in.plannedPacks() <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION, "Sản lượng kế hoạch phải lớn hơn 0");
            }
            if (!seen.add(pid + "|" + in.planDate())) {
                throw new BusinessException(ErrorCode.VALIDATION, "Sản phẩm bị lặp trong cùng ngày");
            }
            productIds.add(pid);
            resolvedIds.add(pid);
        }
        Map<UUID, ProductInfo> infos = masterdata.products(productIds);
        for (int i = 0; i < items.size(); i++) {
            ProductInfo info = infos.get(resolvedIds.get(i));
            if (info == null || !info.active()) {
                throw new BusinessException(ErrorCode.VALIDATION, "Sản phẩm không tồn tại hoặc đã ngừng");
            }
            ItemInput in = items.get(i);
            jdbc.update("INSERT INTO pl_weekly_plan_item(id, weekly_plan_id, product_id, plan_date, planned_packs) "
                            + "VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?)",
                    UuidCreator.getTimeOrderedEpoch().toString(), planId.toString(), resolvedIds.get(i).toString(),
                    in.planDate(), in.plannedPacks());
        }
    }

    private static void requireStatus(Map<String, Object> plan, String expected) {
        if (!expected.equals(plan.get("status"))) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Thao tác chỉ áp dụng cho KHSX tuần ở trạng thái " + expected + " (hiện tại "
                            + plan.get("status") + ")");
        }
    }

    private String user() {
        return currentUser.userId().map(UUID::toString).orElse(null);
    }

    private void audit(UUID id, String action, List<FieldChange> changes) {
        var principal = currentUser.principal();
        audit.write(new AuditLogWriter.Entry("pl", "WeeklyPlan", id.toString(), action, changes, null,
                principal.map(p -> p.userId()).orElse(null), null, principal.map(p -> p.deviceId()).orElse(null),
                null));
    }
}
