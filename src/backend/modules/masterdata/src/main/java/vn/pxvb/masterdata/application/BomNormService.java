package vn.pxvb.masterdata.application;

import com.github.f4b6a3.uuid.UuidCreator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import vn.pxvb.common.web.PageResponse;
import vn.pxvb.masterdata.contract.MasterdataQueries.Norm;
import vn.pxvb.masterdata.contract.MasterdataQueries.NormLine;

/**
 * Định mức NVL có phiên bản (FR-MD-007, BR-MD-004, CC-12): DRAFT → PENDING → ACTIVE; kích hoạt bản mới thì bản
 * đang ACTIVE chuyển RETIRED với {@code effective_to = effective_from} của bản mới. Lệnh SX tham chiếu phiên bản
 * hiệu lực tại thời điểm xác nhận nên không đổi khi có bản mới (AC-MD-003).
 */
@Service
public class BomNormService {

    public record LineInput(UUID materialId, String materialCode, BigDecimal qtyPer1000Packs, String uom,
                            String consumerType) {
    }

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String HEADER_SQL = "SELECT BIN_TO_UUID(n.id) AS id, BIN_TO_UUID(n.product_id) AS productId, "
            + "p.code AS productCode, p.name AS productName, n.version_no AS versionNo, "
            + "n.effective_from AS effectiveFrom, n.effective_to AS effectiveTo, n.status AS status, "
            + "BIN_TO_UUID(n.approved_by) AS approvedBy, n.approved_at AS approvedAt, "
            + "n.change_reason AS changeReason, n.row_version AS rowVersion "
            + "FROM md_bom_norm n JOIN md_product p ON p.id = n.product_id";

    private final JdbcTemplate jdbc;
    private final AuditLogWriter audit;
    private final CurrentUser currentUser;
    private final Clock clock;

    public BomNormService(JdbcTemplate jdbc, AuditLogWriter audit, CurrentUser currentUser, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // ---------- Truy vấn ----------

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(UUID productId, String status, int page, int pageSize) {
        List<String> where = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (productId != null) {
            where.add("n.product_id = UUID_TO_BIN(?)");
            params.add(productId.toString());
        }
        if (status != null && !status.isBlank()) {
            where.add("n.status = ?");
            params.add(status);
        }
        String whereSql = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM md_bom_norm n" + whereSql, Long.class,
                params.toArray());
        int size = Math.min(Math.max(pageSize, 1), 200);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add(Math.max(page, 0) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(HEADER_SQL + whereSql
                + " ORDER BY p.code, n.version_no DESC LIMIT ? OFFSET ?", pageParams.toArray());
        return new PageResponse<>(rows, Math.max(page, 0), size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList(HEADER_SQL + " WHERE n.id = UUID_TO_BIN(?)", id.toString());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy định mức");
        }
        Map<String, Object> view = new LinkedHashMap<>(rows.get(0));
        view.put("lines", lines(id));
        return view;
    }

    private List<Map<String, Object>> lines(UUID normId) {
        return jdbc.queryForList("SELECT BIN_TO_UUID(l.material_id) AS materialId, m.code AS materialCode, "
                + "m.name AS materialName, l.qty_per_1000_packs AS qtyPer1000Packs, l.uom AS uom, "
                + "l.consumer_type AS consumerType FROM md_bom_norm_line l JOIN md_material m ON m.id = l.material_id "
                + "WHERE l.norm_id = UUID_TO_BIN(?) ORDER BY m.code", normId.toString());
    }

    /** Định mức hiệu lực tại {@code asOf}: gồm bản ACTIVE và bản RETIRED còn trong khoảng hiệu lực. */
    @Transactional(readOnly = true)
    public Optional<Norm> active(UUID productId, LocalDate asOf) {
        List<Map<String, Object>> headers = jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, version_no AS versionNo, "
                + "effective_from AS effectiveFrom FROM md_bom_norm WHERE product_id = UUID_TO_BIN(?) "
                + "AND status IN ('ACTIVE','RETIRED') AND effective_from <= ? "
                + "AND (effective_to IS NULL OR effective_to > ?) ORDER BY version_no DESC LIMIT 1",
                productId.toString(), asOf, asOf);
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> h = headers.get(0);
        UUID normId = UUID.fromString((String) h.get("id"));
        List<NormLine> lines = lines(normId).stream().map(l -> new NormLine(
                UUID.fromString((String) l.get("materialId")), (String) l.get("materialCode"),
                (BigDecimal) l.get("qtyPer1000Packs"), (String) l.get("uom"), (String) l.get("consumerType")))
                .toList();
        return Optional.of(new Norm(normId, productId, ((Number) h.get("versionNo")).intValue(),
                ((java.sql.Date) h.get("effectiveFrom")).toLocalDate(), lines));
    }

    // ---------- Soạn thảo ----------

    @Transactional
    public Map<String, Object> create(UUID productId, LocalDate effectiveFrom, String changeReason,
                                      List<LineInput> lines) {
        requireActiveProduct(productId);
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(version_no), 0) FROM md_bom_norm "
                + "WHERE product_id = UUID_TO_BIN(?)", Integer.class, productId.toString());
        UUID id = UuidCreator.getTimeOrderedEpoch();
        jdbc.update("INSERT INTO md_bom_norm(id, row_version, created_at, created_by, updated_at, updated_by, "
                        + "product_id, version_no, effective_from, status, change_reason) VALUES (UUID_TO_BIN(?), 0, "
                        + "UTC_TIMESTAMP(6), UUID_TO_BIN(?), UTC_TIMESTAMP(6), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, "
                        + "'DRAFT', ?)", id.toString(), user(), user(), productId.toString(),
                (max == null ? 0 : max) + 1, effectiveFrom, changeReason);
        writeLines(id, lines);
        audit(id, "CREATE", List.of(new FieldChange("productId", null, productId.toString()),
                new FieldChange("lines", null, lines.size())));
        return get(id);
    }

    @Transactional
    public Map<String, Object> replaceLines(UUID id, List<LineInput> lines, LocalDate effectiveFrom,
                                            String changeReason) {
        Map<String, Object> header = get(id);
        requireStatus(header, "DRAFT");
        jdbc.update("DELETE FROM md_bom_norm_line WHERE norm_id = UUID_TO_BIN(?)", id.toString());
        writeLines(id, lines);
        jdbc.update("UPDATE md_bom_norm SET effective_from = COALESCE(?, effective_from), "
                        + "change_reason = COALESCE(?, change_reason), row_version = row_version + 1, "
                        + "updated_at = UTC_TIMESTAMP(6), updated_by = UUID_TO_BIN(?) WHERE id = UUID_TO_BIN(?)",
                effectiveFrom, changeReason, user(), id.toString());
        audit(id, "UPDATE_LINES", List.of(new FieldChange("lines", header.get("lines") == null ? 0
                : ((List<?>) header.get("lines")).size(), lines.size())));
        return get(id);
    }

    @Transactional
    public Map<String, Object> submit(UUID id) {
        Map<String, Object> header = get(id);
        requireStatus(header, "DRAFT");
        if (((List<?>) header.get("lines")).isEmpty()) {
            throw new BusinessException(ErrorCode.MD_NORM_INVALID, "Định mức chưa có dòng nguyên vật liệu nào");
        }
        setStatus(id, "PENDING");
        audit(id, "SUBMIT", List.of(new FieldChange("status", "DRAFT", "PENDING")));
        return get(id);
    }

    @Transactional
    public Map<String, Object> reject(UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cần nhập lý do từ chối",
                    Map.of("reason", List.of("Vui lòng nhập lý do")));
        }
        requireStatus(get(id), "PENDING");
        jdbc.update("UPDATE md_bom_norm SET status = 'DRAFT', change_reason = ?, row_version = row_version + 1, "
                + "updated_at = UTC_TIMESTAMP(6), updated_by = UUID_TO_BIN(?) WHERE id = UUID_TO_BIN(?)",
                "Bị từ chối: " + reason.trim(), user(), id.toString());
        audit(id, "REJECT", List.of(new FieldChange("status", "PENDING", "DRAFT")));
        return get(id);
    }

    @Transactional
    public Map<String, Object> approve(UUID id, boolean canBackdate) {
        Map<String, Object> header = get(id);
        requireStatus(header, "PENDING");
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        LocalDate from = header.get("effectiveFrom") == null ? today
                : ((java.sql.Date) header.get("effectiveFrom")).toLocalDate();
        if (from.isBefore(today) && !canBackdate) {
            throw new BusinessException(ErrorCode.MD_NORM_INVALID,
                    "Ngày hiệu lực không được ở quá khứ (cần quyền đặc biệt kèm lý do)");
        }
        UUID productId = UUID.fromString((String) header.get("productId"));
        jdbc.update("UPDATE md_bom_norm SET status = 'RETIRED', effective_to = ?, row_version = row_version + 1, "
                        + "updated_at = UTC_TIMESTAMP(6), updated_by = UUID_TO_BIN(?) "
                        + "WHERE product_id = UUID_TO_BIN(?) AND status = 'ACTIVE' AND id <> UUID_TO_BIN(?)",
                from, user(), productId.toString(), id.toString());
        jdbc.update("UPDATE md_bom_norm SET status = 'ACTIVE', effective_from = ?, approved_by = UUID_TO_BIN(?), "
                        + "approved_at = UTC_TIMESTAMP(6), row_version = row_version + 1, "
                        + "updated_at = UTC_TIMESTAMP(6), updated_by = UUID_TO_BIN(?) WHERE id = UUID_TO_BIN(?)",
                from, user(), user(), id.toString());
        audit(id, "APPROVE", List.of(new FieldChange("status", "PENDING", "ACTIVE"),
                new FieldChange("effectiveFrom", header.get("effectiveFrom"), from.toString())));
        return get(id);
    }

    @Transactional
    public Map<String, Object> cloneNorm(UUID id) {
        Map<String, Object> source = get(id);
        UUID productId = UUID.fromString((String) source.get("productId"));
        List<LineInput> lines = lines(id).stream().map(l -> new LineInput(
                UUID.fromString((String) l.get("materialId")), null, (BigDecimal) l.get("qtyPer1000Packs"),
                (String) l.get("uom"), (String) l.get("consumerType"))).toList();
        return create(productId, null, "Nhân bản từ phiên bản " + source.get("versionNo"), lines);
    }

    /** So sánh hai phiên bản (cùng hoặc khác sản phẩm): chênh lệch từng NVL. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> compare(UUID a, UUID b) {
        get(a);
        get(b);
        Map<String, BigDecimal> qa = qtyByMaterial(a);
        Map<String, BigDecimal> qb = qtyByMaterial(b);
        Set<String> codes = new HashSet<>(qa.keySet());
        codes.addAll(qb.keySet());
        return codes.stream().sorted().map(code -> {
            BigDecimal x = qa.get(code);
            BigDecimal y = qb.get(code);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("materialCode", code);
            row.put("qtyA", x);
            row.put("qtyB", y);
            row.put("diff", (y == null ? BigDecimal.ZERO : y).subtract(x == null ? BigDecimal.ZERO : x));
            row.put("change", x == null ? "ADDED" : y == null ? "REMOVED"
                    : x.compareTo(y) == 0 ? "SAME" : "CHANGED");
            return row;
        }).toList();
    }

    private Map<String, BigDecimal> qtyByMaterial(UUID normId) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        lines(normId).forEach(l -> map.put((String) l.get("materialCode"), (BigDecimal) l.get("qtyPer1000Packs")));
        return map;
    }

    // ---------- Nội bộ ----------

    private void writeLines(UUID normId, List<LineInput> lines) {
        Set<UUID> seen = new HashSet<>();
        for (LineInput in : lines) {
            Map<String, Object> material = resolveMaterial(in);
            UUID materialId = UUID.fromString((String) material.get("id"));
            if (!seen.add(materialId)) {
                throw new BusinessException(ErrorCode.MD_NORM_INVALID,
                        "Nguyên vật liệu " + material.get("code") + " bị lặp trong định mức");
            }
            if (in.qtyPer1000Packs() == null || in.qtyPer1000Packs().signum() <= 0) {
                throw new BusinessException(ErrorCode.MD_NORM_INVALID,
                        "Định mức của " + material.get("code") + " phải lớn hơn 0");
            }
            String consumer = in.consumerType() == null || in.consumerType().isBlank() ? null
                    : in.consumerType().toUpperCase();
            if (consumer != null && !Set.of("MAKER", "PACKER").contains(consumer)) {
                throw new BusinessException(ErrorCode.MD_NORM_INVALID,
                        "Loại máy tiêu thụ phải là MAKER hoặc PACKER");
            }
            String uom = in.uom() == null || in.uom().isBlank() ? (String) material.get("uom") : in.uom();
            jdbc.update("INSERT INTO md_bom_norm_line(id, norm_id, material_id, qty_per_1000_packs, uom, "
                            + "consumer_type) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?)",
                    UuidCreator.getTimeOrderedEpoch().toString(), normId.toString(), materialId.toString(),
                    in.qtyPer1000Packs(), uom, consumer);
        }
    }

    private Map<String, Object> resolveMaterial(LineInput in) {
        List<Map<String, Object>> rows;
        if (in.materialId() != null) {
            rows = jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, code, uom, is_active AS active FROM md_material "
                    + "WHERE id = UUID_TO_BIN(?)", in.materialId().toString());
        } else {
            rows = jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, code, uom, is_active AS active FROM md_material "
                    + "WHERE code = ?", in.materialCode());
        }
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.MD_NORM_INVALID, "Nguyên vật liệu không tồn tại: "
                    + (in.materialId() != null ? in.materialId() : in.materialCode()));
        }
        return rows.get(0);
    }

    private void requireActiveProduct(UUID productId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM md_product WHERE id = UUID_TO_BIN(?) AND is_active = 1",
                Long.class, productId.toString());
        if (n == null || n == 0) {
            throw new BusinessException(ErrorCode.VALIDATION, "Sản phẩm không tồn tại hoặc đã ngừng");
        }
    }

    private void requireStatus(Map<String, Object> header, String expected) {
        if (!expected.equals(header.get("status"))) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Thao tác chỉ áp dụng cho định mức ở trạng thái " + expected + " (hiện tại "
                            + header.get("status") + ")");
        }
    }

    private void setStatus(UUID id, String status) {
        jdbc.update("UPDATE md_bom_norm SET status = ?, row_version = row_version + 1, updated_at = UTC_TIMESTAMP(6), "
                + "updated_by = UUID_TO_BIN(?) WHERE id = UUID_TO_BIN(?)", status, user(), id.toString());
    }

    private String user() {
        return currentUser.userId().map(UUID::toString).orElse(null);
    }

    private void audit(UUID id, String action, List<FieldChange> changes) {
        var principal = currentUser.principal();
        audit.write(new AuditLogWriter.Entry("md", "BomNorm", id.toString(), action, changes, null,
                principal.map(p -> p.userId()).orElse(null), null, principal.map(p -> p.deviceId()).orElse(null),
                null));
    }
}
