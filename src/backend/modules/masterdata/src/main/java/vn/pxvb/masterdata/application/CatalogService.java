package vn.pxvb.masterdata.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.audit.AuditLogWriter;
import vn.pxvb.common.audit.AuditLogWriter.FieldChange;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.common.web.PageResponse;
import vn.pxvb.dataio.application.ExportService;
import vn.pxvb.masterdata.application.CatalogSpec.Col;
import vn.pxvb.masterdata.application.CatalogSpec.Type;
import vn.pxvb.masterdata.contract.UsageChecker;

/**
 * Động cơ danh mục masterdata (FR-MD-001…022): CRUD theo đặc tả, tìm kiếm/phân trang phía server, khóa lạc quan
 * bằng RowVersion, kiểm tra tham chiếu trước khi khóa (BR-MD-003), phiên bản hóa (CC-12), audit từng trường,
 * import/export. Danh mục không bị xóa vật lý nếu có tham chiếu (BR-MD-003).
 */
@Service
public class CatalogService {

    public record CatalogChanged(String resource) {
    }

    private static final Set<String> SYSTEM_KEYS = Set.of("id", "rowVersion", "createdAt", "createdBy", "updatedAt",
            "updatedBy", "active");
    private static final int MAX_PAGE_SIZE = 200;
    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditLogWriter audit;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;
    private final List<UsageChecker> checkers;
    private final ExportService exports;
    private final CatalogReferenceChecks references;

    public CatalogService(JdbcTemplate jdbc, ObjectMapper mapper, AuditLogWriter audit, CurrentUser currentUser,
                          ApplicationEventPublisher events, List<UsageChecker> checkers, ExportService exports) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
        this.currentUser = currentUser;
        this.events = events;
        this.checkers = checkers;
        this.exports = exports;
        this.references = new CatalogReferenceChecks(jdbc);
    }

    public CatalogSpec spec(String resource) {
        CatalogSpec spec = Catalogs.byResource(resource);
        if (spec == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không có danh mục: " + resource);
        }
        return spec;
    }

    // ---------- Truy vấn ----------

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> list(String resource, String q, Map<String, String> filters,
                                                  boolean activeOnly, int page, int pageSize, String sort) {
        CatalogSpec spec = spec(resource);
        List<String> where = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            List<String> likes = new ArrayList<>();
            for (Col c : spec.cols()) {
                if (c.searchable()) {
                    likes.add("t." + c.column() + " LIKE ?");
                    params.add("%" + escapeLike(q.trim()) + "%");
                }
            }
            if (!likes.isEmpty()) {
                where.add("(" + String.join(" OR ", likes) + ")");
            }
        }
        for (Map.Entry<String, String> f : filters.entrySet()) {
            Col c = spec.col(f.getKey());
            if (c == null || c.type() == Type.JSON || f.getValue() == null || f.getValue().isBlank()) {
                continue;
            }
            if (c.type() == Type.REF || c.type() == Type.UUID) {
                where.add("t." + c.column() + " = UUID_TO_BIN(?)");
            } else {
                where.add("t." + c.column() + " = ?");
            }
            params.add(f.getValue());
        }
        if (activeOnly && spec.hasActive()) {
            where.add("t.is_active = 1");
        }
        String whereSql = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + spec.table() + " t" + whereSql, Long.class,
                params.toArray());
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int offset = Math.max(page, 0) * size;
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add(offset);
        List<Map<String, Object>> rows = jdbc.query("SELECT " + selectList(spec) + " FROM " + spec.table() + " t"
                + whereSql + " ORDER BY " + orderBy(spec, sort) + " LIMIT ? OFFSET ?",
                (rs, i) -> mapRow(spec, rs), pageParams.toArray());
        return new PageResponse<>(rows, Math.max(page, 0), size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(String resource, UUID id) {
        return getRow(spec(resource), id);
    }

    private Map<String, Object> getRow(CatalogSpec spec, UUID id) {
        List<Map<String, Object>> rows = jdbc.query("SELECT " + selectList(spec) + " FROM " + spec.table()
                + " t WHERE t.id = UUID_TO_BIN(?)", (rs, i) -> mapRow(spec, rs), id.toString());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy " + spec.title());
        }
        return rows.get(0);
    }

    // ---------- Ghi ----------

    @Transactional
    public Map<String, Object> create(String resource, Map<String, Object> body) {
        CatalogSpec spec = spec(resource);
        Map<String, List<String>> errors = new LinkedHashMap<>();
        Map<String, Object> typed = coerce(spec, body, true, false, errors);
        validateRules(spec, typed, errors);
        throwIfErrors(errors);
        UUID id = insertTyped(spec, typed);
        return getRow(spec, id);
    }

    @Transactional
    public Map<String, Object> update(String resource, UUID id, Map<String, Object> body, Long ifMatch) {
        CatalogSpec spec = spec(resource);
        Map<String, Object> existing = getRow(spec, id);
        if (spec.versioned() && !"DRAFT".equals(existing.get("status"))) {
            throw new BusinessException(ErrorCode.DATA_LOCKED,
                    "Phiên bản đã kích hoạt, hãy nhân bản sang phiên bản mới để chỉnh sửa");
        }
        Map<String, List<String>> errors = new LinkedHashMap<>();
        Map<String, Object> typed = coerce(spec, body, false, false, errors);
        for (Col c : spec.cols()) {
            if (c.immutable() && typed.containsKey(c.name())
                    && !Objects.equals(comparable(existing.get(c.name())), comparable(typed.get(c.name())))) {
                errors.computeIfAbsent(c.name(), k -> new ArrayList<>()).add("Không được thay đổi " + c.label());
            }
        }
        Map<String, Object> merged = new LinkedHashMap<>(existing);
        merged.putAll(typed);
        validateRules(spec, merged, errors);
        throwIfErrors(errors);
        Long version = ifMatch != null ? ifMatch : toLong(body.get("rowVersion"));
        updateTyped(spec, id, existing, typed, version);
        return getRow(spec, id);
    }

    @Transactional
    public Map<String, Object> setActive(String resource, UUID id, boolean active) {
        CatalogSpec spec = spec(resource);
        Map<String, Object> existing = getRow(spec, id);
        if (!spec.hasActive()) {
            throw new BusinessException(ErrorCode.VALIDATION, spec.title() + " không hỗ trợ khóa/mở khóa");
        }
        if (!active) {
            List<String> usages = findUsages(spec, id);
            if (!usages.isEmpty()) {
                throw new BusinessException(ErrorCode.MD_IN_USE, "Đang được sử dụng tại: "
                        + String.join("; ", usages), Map.of("usages", usages));
            }
        }
        String statusSql = spec.statusProp() != null
                ? ", " + CatalogSpec.snake(spec.statusProp()) + " = ?" : "";
        List<Object> params = new ArrayList<>();
        params.add(active);
        if (spec.statusProp() != null) {
            params.add(active ? spec.activeStatus() : spec.inactiveStatus());
        }
        params.add(currentUser.userId().map(UUID::toString).orElse(null));
        params.add(id.toString());
        jdbc.update("UPDATE " + spec.table() + " SET is_active = ?" + statusSql + ", row_version = row_version + 1, "
                + "updated_at = UTC_TIMESTAMP(6), updated_by = UUID_TO_BIN(?) WHERE id = UUID_TO_BIN(?)",
                params.toArray());
        audit(spec, id, active ? "ACTIVATE" : "DEACTIVATE", List.of(
                new FieldChange("active", existing.get("active"), active)));
        events.publishEvent(new CatalogChanged(spec.resource()));
        return getRow(spec, id);
    }

    @Transactional
    public void delete(String resource, UUID id) {
        CatalogSpec spec = spec(resource);
        if (spec.hasActive() || spec.versioned() || "thresholds".equals(spec.resource())) {
            throw new BusinessException(ErrorCode.VALIDATION,
                    spec.title() + " không được xóa; hãy khóa hoặc đổi trạng thái");
        }
        getRow(spec, id);
        jdbc.update("DELETE FROM " + spec.table() + " WHERE id = UUID_TO_BIN(?)", id.toString());
        audit(spec, id, "DELETE", List.of());
        events.publishEvent(new CatalogChanged(spec.resource()));
    }

    // ---------- Phiên bản hóa (CC-12) ----------

    @Transactional
    public Map<String, Object> cloneVersion(String resource, UUID id) {
        CatalogSpec spec = spec(resource);
        if (!spec.versioned()) {
            throw new BusinessException(ErrorCode.VALIDATION, spec.title() + " không có phiên bản");
        }
        Map<String, Object> source = getRow(spec, id);
        Map<String, Object> typed = fromRow(spec, source);
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(version_no), 0) FROM " + spec.table()
                + " WHERE code = ?", Integer.class, source.get("code"));
        typed.put("versionNo", (max == null ? 0 : max) + 1);
        typed.put("status", "DRAFT");
        typed.put("effectiveFrom", null);
        typed.put("effectiveTo", null);
        UUID created = insertTyped(spec, typed);
        return getRow(spec, created);
    }

    @Transactional
    public Map<String, Object> activateVersion(String resource, UUID id, LocalDate effectiveFrom) {
        CatalogSpec spec = spec(resource);
        if (!spec.versioned()) {
            throw new BusinessException(ErrorCode.VALIDATION, spec.title() + " không có phiên bản");
        }
        Map<String, Object> row = getRow(spec, id);
        if (!"DRAFT".equals(row.get("status"))) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Chỉ kích hoạt được bản nháp");
        }
        LocalDate from = effectiveFrom != null ? effectiveFrom
                : row.get("effectiveFrom") != null ? LocalDate.parse((String) row.get("effectiveFrom"))
                : LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        String user = currentUser.userId().map(UUID::toString).orElse(null);
        jdbc.update("UPDATE " + spec.table() + " SET status = 'RETIRED', effective_to = ?, "
                        + "row_version = row_version + 1, updated_at = UTC_TIMESTAMP(6), updated_by = UUID_TO_BIN(?) "
                        + "WHERE code = ? AND status = 'ACTIVE' AND id <> UUID_TO_BIN(?)",
                from, user, row.get("code"), id.toString());
        jdbc.update("UPDATE " + spec.table() + " SET status = 'ACTIVE', effective_from = ?, "
                        + "row_version = row_version + 1, updated_at = UTC_TIMESTAMP(6), updated_by = UUID_TO_BIN(?) "
                        + "WHERE id = UUID_TO_BIN(?)", from, user, id.toString());
        audit(spec, id, "ACTIVATE_VERSION", List.of(new FieldChange("status", "DRAFT", "ACTIVE"),
                new FieldChange("effectiveFrom", row.get("effectiveFrom"), from.toString())));
        events.publishEvent(new CatalogChanged(spec.resource()));
        return getRow(spec, id);
    }

    // ---------- Import / export ----------

    @Transactional(readOnly = true)
    public byte[] export(String resource, String q, Map<String, String> filters) {
        CatalogSpec spec = spec(resource);
        List<String> exprs = new ArrayList<>();
        List<String> headers = new ArrayList<>();
        for (Col c : spec.cols()) {
            headers.add(c.type() == Type.REF ? c.label() + " (mã)" : c.label());
            exprs.add(switch (c.type()) {
                case REF -> "(SELECT r." + c.refKey() + " FROM " + c.refTable() + " r WHERE r.id = t."
                        + c.column() + ")";
                case UUID -> "BIN_TO_UUID(t." + c.column() + ")";
                case JSON -> "CAST(t." + c.column() + " AS CHAR)";
                default -> "t." + c.column();
            });
        }
        if (spec.hasActive()) {
            headers.add("Đang hoạt động");
            exprs.add("t.is_active");
        }
        List<String> where = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            List<String> likes = new ArrayList<>();
            for (Col c : spec.cols()) {
                if (c.searchable()) {
                    likes.add("t." + c.column() + " LIKE ?");
                    params.add("%" + escapeLike(q.trim()) + "%");
                }
            }
            if (!likes.isEmpty()) {
                where.add("(" + String.join(" OR ", likes) + ")");
            }
        }
        for (Map.Entry<String, String> f : filters.entrySet()) {
            Col c = spec.col(f.getKey());
            if (c != null && c.type() != Type.JSON && f.getValue() != null && !f.getValue().isBlank()) {
                where.add(c.type() == Type.REF || c.type() == Type.UUID
                        ? "t." + c.column() + " = UUID_TO_BIN(?)" : "t." + c.column() + " = ?");
                params.add(f.getValue());
            }
        }
        String sql = "SELECT " + String.join(", ", exprs) + " FROM " + spec.table() + " t"
                + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where))
                + " ORDER BY " + orderBy(spec, null);
        int width = exprs.size();
        List<List<Object>> rows = jdbc.query(sql, (rs, i) -> {
            List<Object> row = new ArrayList<>(width);
            for (int k = 1; k <= width; k++) {
                Object v = rs.getObject(k);
                if (v instanceof java.sql.Date date) {
                    v = date.toLocalDate();
                } else if (v instanceof Time time) {
                    v = time.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
                }
                row.add(v);
            }
            return row;
        }, params.toArray());
        String sheet = spec.title().length() > 31 ? spec.title().substring(0, 31) : spec.title();
        return exports.workbook(sheet, headers, rows);
    }

    /** Lỗi theo khóa cột import; rỗng nếu hợp lệ (kể cả kiểm tra tham chiếu). */
    @Transactional(readOnly = true)
    public Map<String, String> validateImportRow(CatalogSpec spec, Map<String, String> row) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        Map<String, Object> raw = new LinkedHashMap<>(row);
        Map<String, Object> typed = coerce(spec, raw, true, true, errors);
        validateRules(spec, typed, errors);
        Map<String, String> flat = new LinkedHashMap<>();
        errors.forEach((k, v) -> flat.put(k, String.join("; ", v)));
        return flat;
    }

    public String naturalKey(CatalogSpec spec, Map<String, String> row) {
        List<String> parts = new ArrayList<>();
        for (String key : spec.keys()) {
            Col c = spec.col(key);
            parts.add(row.getOrDefault(c.importKey(), ""));
        }
        return String.join("|", parts);
    }

    @Transactional
    public void upsertByKey(CatalogSpec spec, Map<String, String> row) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        Map<String, Object> typed = coerce(spec, new LinkedHashMap<>(row), true, true, errors);
        throwIfErrors(errors);
        Optional<UUID> existing = findByKeys(spec, typed, null);
        if (existing.isPresent()) {
            Map<String, Object> current = getRow(spec, existing.get());
            if (spec.versioned() && !"DRAFT".equals(current.get("status"))) {
                throw new BusinessException(ErrorCode.DATA_LOCKED,
                        "Phiên bản " + current.get("code") + " đã kích hoạt, không thể nhập đè");
            }
            Map<String, Object> changes = new LinkedHashMap<>(typed);
            spec.cols().stream().filter(Col::immutable).forEach(c -> changes.remove(c.name()));
            updateTyped(spec, existing.get(), current, changes, null);
        } else {
            insertTyped(spec, typed);
        }
    }

    public record Slice(List<Map<String, Object>> rows, String nextCursor) {
    }

    /**
     * Các dòng đổi sau {@code since} (khóa phân trang là id UUIDv7 tăng dần) — dùng cho đồng bộ tablet. Danh mục
     * khóa mềm bằng {@code is_active} nên bản ghi bị ngừng vẫn xuất hiện (là tombstone mềm).
     */
    @Transactional(readOnly = true)
    public Slice changedSince(String resource, java.time.Instant since, String cursor, int limit) {
        CatalogSpec spec = spec(resource);
        List<String> where = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (since != null) {
            where.add("t.updated_at > ?");
            params.add(java.sql.Timestamp.from(since));
        }
        if (cursor != null && !cursor.isBlank()) {
            where.add("t.id > UUID_TO_BIN(?)");
            params.add(cursor);
        }
        params.add(limit + 1);
        List<Map<String, Object>> rows = jdbc.query("SELECT " + selectList(spec) + " FROM " + spec.table() + " t"
                + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where)) + " ORDER BY t.id LIMIT ?",
                (rs, i) -> mapRow(spec, rs), params.toArray());
        if (rows.size() > limit) {
            List<Map<String, Object>> page = new ArrayList<>(rows.subList(0, limit));
            return new Slice(page, (String) page.get(page.size() - 1).get("id"));
        }
        return new Slice(rows, null);
    }

    // ---------- Tiện ích dùng chung cho các dịch vụ khác ----------

    @Transactional(readOnly = true)
    public Optional<UUID> findIdByKey(String table, String column, String value) {
        List<String> ids = jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM " + table + " WHERE " + column + " = ?",
                String.class, value);
        return ids.stream().findFirst().map(UUID::fromString);
    }

    // ---------- Nội bộ: ghi ----------

    private UUID insertTyped(CatalogSpec spec, Map<String, Object> typed) {
        Optional<UUID> dup = findByKeys(spec, typed, null);
        if (dup.isPresent()) {
            throw new BusinessException(ErrorCode.MD_DUPLICATE, "Đã tồn tại " + spec.title() + " cùng khóa: "
                    + spec.keys().stream().map(k -> k + "=" + typed.get(k)).toList());
        }
        UUID id = UuidCreator.getTimeOrderedEpoch();
        List<String> cols = new ArrayList<>(List.of("id", "row_version", "created_at", "created_by", "updated_at",
                "updated_by"));
        List<String> placeholders = new ArrayList<>(List.of("UUID_TO_BIN(?)", "0", "UTC_TIMESTAMP(6)",
                "UUID_TO_BIN(?)", "UTC_TIMESTAMP(6)", "UUID_TO_BIN(?)"));
        String user = currentUser.userId().map(UUID::toString).orElse(null);
        List<Object> params = new ArrayList<>(List.of(id.toString()));
        params.add(user);
        params.add(user);
        if (spec.hasActive()) {
            cols.add("is_active");
            placeholders.add("1");
        }
        List<FieldChange> changes = new ArrayList<>();
        for (Col c : spec.cols()) {
            if (!typed.containsKey(c.name())) {
                continue;
            }
            Object v = typed.get(c.name());
            cols.add(c.column());
            placeholders.add(placeholder(c));
            params.add(sqlValue(c, v));
            changes.add(new FieldChange(c.name(), null, v));
        }
        try {
            jdbc.update("INSERT INTO " + spec.table() + "(" + String.join(", ", cols) + ") VALUES ("
                    + String.join(", ", placeholders) + ")", params.toArray());
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.MD_DUPLICATE, "Đã tồn tại " + spec.title() + " cùng khóa");
        }
        audit(spec, id, "CREATE", changes);
        events.publishEvent(new CatalogChanged(spec.resource()));
        return id;
    }

    private void updateTyped(CatalogSpec spec, UUID id, Map<String, Object> existing, Map<String, Object> typed,
                             Long version) {
        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        List<FieldChange> changes = new ArrayList<>();
        for (Col c : spec.cols()) {
            if (!typed.containsKey(c.name())) {
                continue;
            }
            Object newValue = typed.get(c.name());
            if (Objects.equals(comparable(existing.get(c.name())), comparable(newValue))) {
                continue;
            }
            sets.add(c.column() + " = " + placeholder(c));
            params.add(sqlValue(c, newValue));
            changes.add(new FieldChange(c.name(), existing.get(c.name()), newValue));
        }
        if (sets.isEmpty()) {
            return;
        }
        Map<String, Object> merged = new LinkedHashMap<>(existing);
        merged.putAll(typed);
        Optional<UUID> dup = findByKeys(spec, merged, id);
        if (dup.isPresent()) {
            throw new BusinessException(ErrorCode.MD_DUPLICATE, "Đã tồn tại " + spec.title() + " cùng khóa");
        }
        sets.add("row_version = row_version + 1");
        sets.add("updated_at = UTC_TIMESTAMP(6)");
        sets.add("updated_by = UUID_TO_BIN(?)");
        params.add(currentUser.userId().map(UUID::toString).orElse(null));
        String sql = "UPDATE " + spec.table() + " SET " + String.join(", ", sets) + " WHERE id = UUID_TO_BIN(?)";
        params.add(id.toString());
        if (version != null) {
            sql += " AND row_version = ?";
            params.add(version);
        }
        int rows = jdbc.update(sql, params.toArray());
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        audit(spec, id, "UPDATE", changes);
        if ("machines".equals(spec.resource()) && typed.containsKey("status")
                && !Objects.equals(existing.get("status"), typed.get("status"))) {
            jdbc.update("INSERT INTO md_machine_status_history(id, machine_id, from_status, to_status, at, by_user) "
                            + "VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, UTC_TIMESTAMP(6), UUID_TO_BIN(?))",
                    UuidCreator.getTimeOrderedEpoch().toString(), id.toString(), existing.get("status"),
                    typed.get("status"), currentUser.userId().map(UUID::toString).orElse(null));
        }
        events.publishEvent(new CatalogChanged(spec.resource()));
    }

    private Optional<UUID> findByKeys(CatalogSpec spec, Map<String, Object> values, UUID excludeId) {
        if (spec.keys().isEmpty()) {
            return Optional.empty();
        }
        List<String> where = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (String key : spec.keys()) {
            Col c = spec.col(key);
            if (!values.containsKey(key) && c.defaultValue() == null && !c.required()) {
                where.add("t." + c.column() + " IS NULL");
                continue;
            }
            where.add("t." + c.column() + " <=> " + placeholder(c));
            params.add(sqlValue(c, values.getOrDefault(key, c.defaultValue())));
        }
        String sql = "SELECT BIN_TO_UUID(t.id) FROM " + spec.table() + " t WHERE " + String.join(" AND ", where);
        if (excludeId != null) {
            sql += " AND t.id <> UUID_TO_BIN(?)";
            params.add(excludeId.toString());
        }
        return jdbc.queryForList(sql + " LIMIT 1", String.class, params.toArray()).stream().findFirst()
                .map(UUID::fromString);
    }

    private List<String> findUsages(CatalogSpec spec, UUID id) {
        List<String> found = new ArrayList<>();
        for (CatalogSpec.Usage u : spec.usages()) {
            int placeholders = (int) u.countSql().chars().filter(ch -> ch == '?').count();
            Object[] args = new Object[placeholders];
            java.util.Arrays.fill(args, id.toString());
            Long count = jdbc.queryForObject(u.countSql(), Long.class, args);
            if (count != null && count > 0) {
                found.add(u.label() + " (" + count + ")");
            }
        }
        for (UsageChecker checker : checkers) {
            if (checker.entityType().equals(spec.entityType())) {
                found.addAll(checker.usages(id));
            }
        }
        return found;
    }

    private void audit(CatalogSpec spec, UUID id, String action, List<FieldChange> changes) {
        var principal = currentUser.principal();
        audit.write(new AuditLogWriter.Entry("md", spec.entityType(), id.toString(), action, changes, null,
                principal.map(p -> p.userId()).orElse(null), null, principal.map(p -> p.deviceId()).orElse(null),
                null));
    }

    // ---------- Nội bộ: ánh xạ & kiểm tra ----------

    private String selectList(CatalogSpec spec) {
        List<String> exprs = new ArrayList<>(List.of("BIN_TO_UUID(t.id) AS id", "t.row_version AS rowVersion",
                "t.created_at AS createdAt", "t.updated_at AS updatedAt"));
        if (spec.hasActive()) {
            exprs.add("t.is_active AS isActive");
        }
        for (Col c : spec.cols()) {
            if (c.type() == Type.REF || c.type() == Type.UUID) {
                exprs.add("BIN_TO_UUID(t." + c.column() + ") AS " + c.name());
            } else if (c.type() == Type.JSON) {
                exprs.add("CAST(t." + c.column() + " AS CHAR) AS " + c.name());
            } else {
                exprs.add("t." + c.column() + " AS " + c.name());
            }
        }
        return String.join(", ", exprs);
    }

    private Map<String, Object> mapRow(CatalogSpec spec, ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getString("id"));
        m.put("rowVersion", rs.getLong("rowVersion"));
        var created = rs.getTimestamp("createdAt");
        var updated = rs.getTimestamp("updatedAt");
        m.put("createdAt", created == null ? null : created.toInstant());
        m.put("updatedAt", updated == null ? null : updated.toInstant());
        if (spec.hasActive()) {
            m.put("active", rs.getBoolean("isActive"));
        }
        for (Col c : spec.cols()) {
            switch (c.type()) {
                case INT -> {
                    int v = rs.getInt(c.name());
                    m.put(c.name(), rs.wasNull() ? null : v);
                }
                case DECIMAL -> m.put(c.name(), rs.getBigDecimal(c.name()));
                case BOOL -> m.put(c.name(), rs.getBoolean(c.name()));
                case DATE -> {
                    var d = rs.getDate(c.name());
                    m.put(c.name(), d == null ? null : d.toLocalDate().toString());
                }
                case TIME -> {
                    Time t = rs.getTime(c.name());
                    m.put(c.name(), t == null ? null : t.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                }
                case JSON -> {
                    String json = rs.getString(c.name());
                    try {
                        m.put(c.name(), json == null ? null : mapper.readTree(json));
                    } catch (JsonProcessingException e) {
                        m.put(c.name(), json);
                    }
                }
                default -> m.put(c.name(), rs.getString(c.name()));
            }
        }
        addStewardship(spec, m, rs);
        return m;
    }

    /** Trường quản trị danh mục (FR-MD-022): người phụ trách, phiên bản và trạng thái. */
    private void addStewardship(CatalogSpec spec, Map<String, Object> m, ResultSet rs) throws SQLException {
        boolean hasVersionNo = spec.versioned() && spec.col("versionNo") != null;
        m.put("owner", spec.owner());
        m.put("version", rs.getLong(hasVersionNo ? "versionNo" : "rowVersion"));
        if (spec.hasActive()) {
            m.putIfAbsent("status", rs.getBoolean("isActive") ? "ACTIVE" : "INACTIVE");
        } else {
            m.putIfAbsent("status", "ACTIVE");
        }
    }

    private String orderBy(CatalogSpec spec, String sort) {
        String direction = "ASC";
        String column = null;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            Col c = spec.col(parts[0].trim());
            if (c != null && c.type() != Type.JSON) {
                column = "t." + c.column();
            }
            if (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())) {
                direction = "DESC";
            }
        }
        if (column == null) {
            Col first = spec.keys().isEmpty() ? null : spec.col(spec.keys().get(0));
            column = first != null && first.type() != Type.REF && first.type() != Type.UUID
                    ? "t." + first.column() : "t.created_at";
        }
        return column + " " + direction + ", t.id";
    }

    private static String placeholder(Col c) {
        return switch (c.type()) {
            case REF, UUID -> "UUID_TO_BIN(?)";
            case JSON -> "CAST(? AS JSON)";
            default -> "?";
        };
    }

    private static Object sqlValue(Col c, Object v) {
        if (v == null) {
            return null;
        }
        return switch (c.type()) {
            case REF, UUID -> v.toString();
            case DATE -> v instanceof LocalDate ? v : LocalDate.parse(v.toString());
            default -> v;
        };
    }

    private static Object comparable(Object v) {
        if (v instanceof BigDecimal d) {
            return d.stripTrailingZeros();
        }
        if (v instanceof JsonNode n) {
            return n.toString();
        }
        if (v instanceof UUID u) {
            return u.toString();
        }
        if (v instanceof LocalDate d) {
            return d.toString();
        }
        if (v instanceof LocalTime t) {
            return t.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        if (v instanceof String s && s.matches("\\d{2}:\\d{2}:\\d{2}")) {
            return s.substring(0, 5);
        }
        return v;
    }

    private Map<String, Object> fromRow(CatalogSpec spec, Map<String, Object> row) {
        Map<String, Object> typed = new LinkedHashMap<>();
        for (Col c : spec.cols()) {
            Object v = row.get(c.name());
            if (v == null) {
                typed.put(c.name(), null);
            } else if (c.type() == Type.JSON) {
                typed.put(c.name(), v.toString());
            } else if (c.type() == Type.DATE) {
                typed.put(c.name(), LocalDate.parse(v.toString()));
            } else {
                typed.put(c.name(), v);
            }
        }
        return typed;
    }

    /**
     * Chuyển dữ liệu thô (JSON body hoặc chuỗi import) sang giá trị có kiểu. Chỉ trả về các cột có mặt trong đầu
     * vào (cập nhật một phần), cộng giá trị mặc định khi tạo mới. Lỗi gom theo tên cột (hoặc khóa cột import).
     */
    private Map<String, Object> coerce(CatalogSpec spec, Map<String, Object> raw, boolean forCreate,
                                       boolean importKeys, Map<String, List<String>> errors) {
        Map<String, Object> typed = new LinkedHashMap<>();
        for (Col c : spec.cols()) {
            String errKey = importKeys ? c.importKey() : c.name();
            String lookupKey = importKeys ? c.importKey() : c.name();
            boolean present = raw.containsKey(lookupKey) || (!importKeys && c.type() == Type.REF
                    && raw.containsKey(c.importKey()));
            Object value = raw.containsKey(lookupKey) ? raw.get(lookupKey) : raw.get(c.importKey());
            boolean viaCode = c.type() == Type.REF && (importKeys
                    || (!raw.containsKey(c.name()) && raw.containsKey(c.importKey())));
            if (value instanceof String s && s.isBlank()) {
                value = null;
            }
            if (!present || value == null) {
                if (!present && forCreate && c.defaultValue() != null) {
                    typed.put(c.name(), c.defaultValue());
                } else if (present) {
                    typed.put(c.name(), null);
                }
                if (c.required() && (forCreate || present) && typed.get(c.name()) == null) {
                    errors.computeIfAbsent(errKey, k -> new ArrayList<>()).add("Bắt buộc nhập " + c.label());
                    typed.remove(c.name());
                }
                continue;
            }
            try {
                typed.put(c.name(), convert(c, value, viaCode));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                errors.computeIfAbsent(errKey, k -> new ArrayList<>()).add(e.getMessage() != null
                        && !(e instanceof DateTimeParseException) ? e.getMessage() : c.label() + " không hợp lệ");
            }
        }
        return typed;
    }

    private Object convert(Col c, Object value, boolean viaCode) {
        String text = value.toString().trim();
        switch (c.type()) {
            case STRING -> {
                if (c.maxLen() > 0 && text.length() > c.maxLen()) {
                    throw new IllegalArgumentException(c.label() + " tối đa " + c.maxLen() + " ký tự");
                }
                return text;
            }
            case INT -> {
                try {
                    return value instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())
                            ? n.intValue() : new BigDecimal(text).intValueExact();
                } catch (ArithmeticException | NumberFormatException e) {
                    throw new IllegalArgumentException(c.label() + " phải là số nguyên");
                }
            }
            case DECIMAL -> {
                try {
                    return new BigDecimal(text.replace(',', '.'));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(c.label() + " phải là số");
                }
            }
            case BOOL -> {
                if (value instanceof Boolean b) {
                    return b;
                }
                String t = text.toLowerCase(Locale.ROOT);
                if (Set.of("true", "1", "có", "co", "yes", "x").contains(t)) {
                    return true;
                }
                if (Set.of("false", "0", "không", "khong", "no").contains(t)) {
                    return false;
                }
                throw new IllegalArgumentException(c.label() + " phải là Có/Không");
            }
            case DATE -> {
                try {
                    return text.contains("/") ? LocalDate.parse(text, VN_DATE) : LocalDate.parse(text);
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException(c.label() + " phải có dạng yyyy-MM-dd hoặc dd/MM/yyyy");
                }
            }
            case TIME -> {
                try {
                    return LocalTime.parse(text.length() == 5 ? text + ":00" : text).format(
                            DateTimeFormatter.ofPattern("HH:mm:ss"));
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException(c.label() + " phải có dạng HH:mm");
                }
            }
            case ENUM -> {
                String upper = text.toUpperCase(Locale.ROOT);
                return c.values().stream().filter(v -> v.equals(upper)).findFirst().orElseThrow(
                        () -> new IllegalArgumentException(c.label() + " phải thuộc: " + String.join(", ", c.values())));
            }
            case CODE_REF -> {
                Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + c.refTable() + " WHERE code = ?", Long.class,
                        text);
                if (n == null || n == 0) {
                    throw new IllegalArgumentException(c.label() + " '" + text + "' không tồn tại");
                }
                return text;
            }
            case REF -> {
                if (viaCode) {
                    return findIdByKey(c.refTable(), c.refKey(), text).orElseThrow(
                            () -> new IllegalArgumentException(c.label() + " '" + text + "' không tồn tại"));
                }
                UUID id;
                try {
                    id = UUID.fromString(text);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(c.label() + " không phải mã định danh hợp lệ");
                }
                Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + c.refTable() + " WHERE id = UUID_TO_BIN(?)",
                        Long.class, id.toString());
                if (n == null || n == 0) {
                    throw new IllegalArgumentException(c.label() + " không tồn tại");
                }
                return id;
            }
            case UUID -> {
                try {
                    return UUID.fromString(text);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(c.label() + " không phải mã định danh hợp lệ");
                }
            }
            case JSON -> {
                try {
                    if (value instanceof String s) {
                        mapper.readTree(s);
                        return s;
                    }
                    return mapper.writeValueAsString(value);
                } catch (JsonProcessingException e) {
                    throw new IllegalArgumentException(c.label() + " không phải JSON hợp lệ");
                }
            }
            default -> throw new IllegalStateException();
        }
    }

    /** Quy tắc nghiệp vụ theo từng danh mục (BR-MD-006, ngưỡng, khung giờ cấp phát…). */
    private void validateRules(CatalogSpec spec, Map<String, Object> v, Map<String, List<String>> errors) {
        switch (spec.resource()) {
            case "machines" -> {
                if (v.get("theoreticalRate") instanceof BigDecimal r && r.signum() <= 0) {
                    add(errors, "theoreticalRate", "Công suất lý thuyết phải lớn hơn 0");
                }
            }
            case "products" -> {
                for (String p : List.of("rodsPerPack", "packsPerCarton", "cartonsPerCase")) {
                    if (v.get(p) instanceof Integer n && n <= 0) {
                        add(errors, p, "Phải lớn hơn 0");
                    }
                }
                if (v.get("stampsPerPack") instanceof Integer n && n < 1) {
                    add(errors, "stampsPerPack", "Phải từ 1 trở lên");
                }
            }
            case "issue-slots" -> {
                if (v.get("slotNo") instanceof Integer n && (n < 1 || n > 4)) {
                    add(errors, "slotNo", "Khung giờ phải từ 1 đến 4");
                }
                if (v.get("sharePct") instanceof BigDecimal p && (p.signum() < 0 || p.compareTo(BigDecimal.valueOf(100)) > 0)) {
                    add(errors, "sharePct", "Tỷ lệ phải từ 0 đến 100");
                }
            }
            case "product-physical-specs" -> {
                if (v.get("minValue") instanceof BigDecimal lo && v.get("maxValue") instanceof BigDecimal hi
                        && lo.compareTo(hi) > 0) {
                    add(errors, "minValue", "Giá trị nhỏ nhất không được lớn hơn giá trị lớn nhất");
                }
            }
            case "thresholds" -> {
                Object type = v.get("scopeType");
                if (("MACHINE".equals(type) || "PRODUCT".equals(type)) && v.get("scopeId") == null) {
                    add(errors, "scopeId", "Cần chọn đối tượng cho phạm vi " + type);
                }
                if (("MODEL".equals(type) || "MATERIAL_GROUP".equals(type) || "STAMP_TYPE".equals(type))
                        && (v.get("scopeValue") == null || v.get("scopeValue").toString().isBlank())) {
                    add(errors, "scopeValue", "Cần nhập giá trị cho phạm vi " + type);
                }
            }
            case "qc-form-templates", "overhaul-forms" -> addAll(errors, "formSchema",
                    QcFormSchemaRules.violations(asTree(v.get("formSchema"))));
            case "maint-task-templates" -> validateMaintTemplate(v, errors);
            case "defect-grade-rules" -> validateDefectRule(v, errors);
            case "machine-part-links" -> validatePartLink(v, errors);
            case "spare-part-min-stock" -> {
                if (v.get("minQty") instanceof BigDecimal q && q.signum() < 0) {
                    add(errors, "minQty", "Tồn tối thiểu không được âm");
                }
            }
            default -> { }
        }
        Object from = v.get("effectiveFrom");
        Object to = v.get("effectiveTo");
        if (from != null && to != null && asDate(from).isAfter(asDate(to))) {
            add(errors, "effectiveTo", "Hiệu lực đến phải sau hiệu lực từ");
        }
    }

    private void validateMaintTemplate(Map<String, Object> v, Map<String, List<String>> errors) {
        JsonNode parts = asTree(v.get("parts"));
        addAll(errors, "checklist", MaintTemplateRules.checklistViolations(asTree(v.get("checklist"))));
        addAll(errors, "parts", MaintTemplateRules.partsViolations(parts));
        addAll(errors, "cycleValue", MaintTemplateRules.cycleViolations(
                v.get("cycleValue") instanceof Integer n ? n : null));
        List<String> unknown = references.unknownPartNos(MaintTemplateRules.partNumbers(parts));
        if (!unknown.isEmpty()) {
            add(errors, "parts", "Phụ tùng không tồn tại: " + String.join(", ", unknown));
        }
    }

    private void validateDefectRule(Map<String, Object> v, Map<String, List<String>> errors) {
        BigDecimal threshold = v.get("thresholdValue") instanceof BigDecimal d ? d : null;
        Integer sampleSize = v.get("sampleSize") instanceof Integer n ? n : null;
        addAll(errors, "thresholdValue",
                DefectGradeRules.violations(str(v.get("metric")), threshold, sampleSize));
        if (v.get("defectCode") == null || v.get("effectiveFrom") == null) {
            return;
        }
        LocalDate to = v.get("effectiveTo") == null ? null : asDate(v.get("effectiveTo"));
        if (references.overlapsExistingGradeRule(v.get("id"), str(v.get("defectCode")), str(v.get("appliesTo")),
                str(v.get("grade")), asDate(v.get("effectiveFrom")), to)) {
            add(errors, "effectiveFrom", "Trùng khoảng hiệu lực với luật khác cùng lỗi, nhóm áp dụng và cấp");
        }
    }

    private void validatePartLink(Map<String, Object> v, Map<String, List<String>> errors) {
        Object machine = v.get("machineId");
        Object component = v.get("componentId");
        String modelCode = str(v.get("modelCode"));
        if (machine == null && (modelCode == null || modelCode.isBlank())) {
            add(errors, "machineId", "Cần chọn máy hoặc nhập dòng máy");
        }
        if (component != null && machine == null) {
            add(errors, "componentId", "Cần chọn máy trước khi chọn cụm");
        } else if (component != null && !references.componentBelongsToMachine(str(component), str(machine))) {
            add(errors, "componentId", "Cụm không thuộc máy đã chọn");
        }
    }

    /** Giá trị JSON có thể là chuỗi (đầu vào mới) hoặc đã phân tích (bản ghi hiện có); chuỗi lỗi đã bị coerce chặn. */
    private JsonNode asTree(Object value) {
        if (value == null || value instanceof JsonNode) {
            return (JsonNode) value;
        }
        try {
            return mapper.readTree(value.toString());
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static void addAll(Map<String, List<String>> errors, String key, List<String> messages) {
        messages.forEach(message -> add(errors, key, message));
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static LocalDate asDate(Object o) {
        return o instanceof LocalDate d ? d : LocalDate.parse(o.toString());
    }

    private static void add(Map<String, List<String>> errors, String key, String message) {
        errors.computeIfAbsent(key, k -> new ArrayList<>()).add(message);
    }

    private static void throwIfErrors(Map<String, List<String>> errors) {
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Dữ liệu không hợp lệ", errors);
        }
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Long toLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o instanceof String s && s.matches("\\d+")) {
            return Long.parseLong(s);
        }
        return null;
    }

    static boolean isSystemKey(String key) {
        return SYSTEM_KEYS.contains(key);
    }
}
