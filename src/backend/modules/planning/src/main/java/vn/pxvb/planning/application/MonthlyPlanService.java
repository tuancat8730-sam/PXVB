package vn.pxvb.planning.application;

import com.github.f4b6a3.uuid.UuidCreator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

/**
 * KHSX tháng (FR-PL-001): PLANNER nhập theo sản phẩm; mỗi lần điều chỉnh sinh {@code Revision} mới và giữ lịch sử;
 * mỗi tháng chỉ có một bản ACTIVE (ràng buộc UNIQUE ở DB).
 */
@Service
public class MonthlyPlanService {

    public record ItemInput(UUID productId, String productCode, long plannedPacks, String note) {
    }

    private final JdbcTemplate jdbc;
    private final MasterdataQueries masterdata;
    private final AuditLogWriter audit;
    private final CurrentUser currentUser;

    public MonthlyPlanService(JdbcTemplate jdbc, MasterdataQueries masterdata, AuditLogWriter audit,
                              CurrentUser currentUser) {
        this.jdbc = jdbc;
        this.masterdata = masterdata;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Transactional
    public Map<String, Object> create(int year, int month, List<ItemInput> items, String sourceNote) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.VALIDATION, "Năm/tháng không hợp lệ");
        }
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION, "KHSX tháng cần có ít nhất một sản phẩm");
        }
        Map<UUID, ItemInput> resolved = resolveItems(items);
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(revision), 0) FROM pl_monthly_plan "
                + "WHERE plan_year = ? AND plan_month = ?", Integer.class, year, month);
        int revision = (max == null ? 0 : max) + 1;
        jdbc.update("UPDATE pl_monthly_plan SET status = 'SUPERSEDED', row_version = row_version + 1, "
                + "updated_at = UTC_TIMESTAMP(6) WHERE plan_year = ? AND plan_month = ? AND status = 'ACTIVE'",
                year, month);
        UUID id = UuidCreator.getTimeOrderedEpoch();
        String code = "KHT-%d%02d-R%d".formatted(year, month, revision);
        String user = user();
        jdbc.update("INSERT INTO pl_monthly_plan(id, row_version, created_at, created_by, updated_at, updated_by, "
                        + "code, plan_year, plan_month, revision, status, source_note) VALUES (UUID_TO_BIN(?), 0, "
                        + "UTC_TIMESTAMP(6), UUID_TO_BIN(?), UTC_TIMESTAMP(6), UUID_TO_BIN(?), ?, ?, ?, ?, 'ACTIVE', ?)",
                id.toString(), user, user, code, year, month, revision, sourceNote);
        for (Map.Entry<UUID, ItemInput> e : resolved.entrySet()) {
            jdbc.update("INSERT INTO pl_monthly_plan_item(id, plan_id, product_id, planned_packs, note) VALUES ("
                            + "UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?)",
                    UuidCreator.getTimeOrderedEpoch().toString(), id.toString(), e.getKey().toString(),
                    e.getValue().plannedPacks(), e.getValue().note());
        }
        audit(id, "CREATE", List.of(new FieldChange("code", null, code),
                new FieldChange("items", null, resolved.size())));
        return get(id);
    }

    @Transactional
    public Map<String, Object> revise(UUID id, List<ItemInput> items, String note) {
        Map<String, Object> current = get(id);
        return create(((Number) current.get("planYear")).intValue(), ((Number) current.get("planMonth")).intValue(),
                items, note == null ? "Điều chỉnh từ " + current.get("code") : note);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Integer year, Integer month) {
        StringBuilder sql = new StringBuilder("SELECT BIN_TO_UUID(id) AS id, code, plan_year AS planYear, "
                + "plan_month AS planMonth, revision, status, source_note AS sourceNote FROM pl_monthly_plan WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (year != null) {
            sql.append(" AND plan_year = ?");
            params.add(year);
        }
        if (month != null) {
            sql.append(" AND plan_month = ?");
            params.add(month);
        }
        sql.append(" ORDER BY plan_year DESC, plan_month DESC, revision DESC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, code, plan_year AS "
                + "planYear, plan_month AS planMonth, revision, status, source_note AS sourceNote, "
                + "row_version AS rowVersion FROM pl_monthly_plan WHERE id = UUID_TO_BIN(?)", id.toString());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy KHSX tháng");
        }
        Map<String, Object> view = new LinkedHashMap<>(rows.get(0));
        view.put("items", items(id));
        return view;
    }

    /** Kế hoạch ↔ đã phân bổ xuống tuần ↔ còn lại (thực tế tích lũy sẽ lấy từ PR khi có). */
    @Transactional(readOnly = true)
    public Map<String, Object> progress(UUID id) {
        Map<String, Object> plan = get(id);
        int year = ((Number) plan.get("planYear")).intValue();
        int month = ((Number) plan.get("planMonth")).intValue();
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.plusMonths(1).minusDays(1);
        Map<String, Long> allocated = new LinkedHashMap<>();
        jdbc.query("SELECT BIN_TO_UUID(i.product_id) AS pid, SUM(i.planned_packs) AS total FROM pl_weekly_plan_item i "
                + "JOIN pl_weekly_plan w ON w.id = i.weekly_plan_id WHERE w.status IN ('SUBMITTED','APPROVED') "
                + "AND i.plan_date BETWEEN ? AND ? GROUP BY i.product_id", rs -> {
            allocated.put(rs.getString("pid"), rs.getLong("total"));
        }, from, to);
        List<Map<String, Object>> rows = new ArrayList<>();
        long planned = 0;
        long allocatedTotal = 0;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) plan.get("items");
        for (Map<String, Object> item : items) {
            long p = ((Number) item.get("plannedPacks")).longValue();
            long a = allocated.getOrDefault((String) item.get("productId"), 0L);
            Map<String, Object> row = new LinkedHashMap<>(item);
            row.put("allocatedToWeeks", a);
            row.put("remaining", p - a);
            row.put("actual", null);
            rows.add(row);
            planned += p;
            allocatedTotal += a;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planId", id.toString());
        result.put("code", plan.get("code"));
        result.put("plannedTotal", planned);
        result.put("allocatedTotal", allocatedTotal);
        result.put("remainingTotal", planned - allocatedTotal);
        result.put("items", rows);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> activePlannedByProduct(int year, int month) {
        Map<UUID, Long> result = new LinkedHashMap<>();
        jdbc.query("SELECT BIN_TO_UUID(i.product_id) AS pid, i.planned_packs AS packs FROM pl_monthly_plan_item i "
                + "JOIN pl_monthly_plan p ON p.id = i.plan_id WHERE p.plan_year = ? AND p.plan_month = ? "
                + "AND p.status = 'ACTIVE'", rs -> {
            result.put(UUID.fromString(rs.getString("pid")), rs.getLong("packs"));
        }, year, month);
        return result;
    }

    private List<Map<String, Object>> items(UUID planId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT BIN_TO_UUID(product_id) AS productId, "
                + "planned_packs AS plannedPacks, note FROM pl_monthly_plan_item WHERE plan_id = UUID_TO_BIN(?)",
                planId.toString());
        Map<UUID, ProductInfo> products = masterdata.products(rows.stream()
                .map(r -> UUID.fromString((String) r.get("productId"))).toList());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>(r);
            ProductInfo p = products.get(UUID.fromString((String) r.get("productId")));
            m.put("productCode", p == null ? null : p.code());
            m.put("productName", p == null ? null : p.name());
            out.add(m);
        }
        out.sort((a, b) -> String.valueOf(a.get("productCode")).compareTo(String.valueOf(b.get("productCode"))));
        return out;
    }

    private Map<UUID, ItemInput> resolveItems(List<ItemInput> items) {
        Map<UUID, ItemInput> resolved = new LinkedHashMap<>();
        Set<UUID> ids = new HashSet<>();
        List<UUID> productIds = new ArrayList<>();
        for (ItemInput in : items) {
            UUID pid = in.productId() != null ? in.productId() : masterdata.productIdByCode(in.productCode())
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION,
                            "Sản phẩm không tồn tại: " + in.productCode()));
            if (in.plannedPacks() <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION, "Sản lượng kế hoạch phải lớn hơn 0");
            }
            if (!ids.add(pid)) {
                throw new BusinessException(ErrorCode.VALIDATION, "Sản phẩm bị lặp trong KHSX tháng");
            }
            productIds.add(pid);
            resolved.put(pid, in);
        }
        Map<UUID, ProductInfo> infos = masterdata.products(productIds);
        for (UUID pid : productIds) {
            ProductInfo info = infos.get(pid);
            if (info == null || !info.active()) {
                throw new BusinessException(ErrorCode.VALIDATION, "Sản phẩm không tồn tại hoặc đã ngừng: " + pid);
            }
        }
        return resolved;
    }

    private String user() {
        return currentUser.userId().map(UUID::toString).orElse(null);
    }

    private void audit(UUID id, String action, List<FieldChange> changes) {
        var principal = currentUser.principal();
        audit.write(new AuditLogWriter.Entry("pl", "MonthlyPlan", id.toString(), action, changes, null,
                principal.map(p -> p.userId()).orElse(null), null, principal.map(p -> p.deviceId()).orElse(null),
                null));
    }
}
