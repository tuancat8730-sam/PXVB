package vn.pxvb.identity.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.audit.AuditLogWriter;
import vn.pxvb.common.audit.AuditLogWriter.FieldChange;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.dataio.application.ExportService;

/**
 * Tra cứu {@code sys_audit_log} (FR-CC-022): lọc, phân trang theo khóa {@code (occurred_at, id)}, lịch sử theo bản
 * ghi, xuất Excel (FR-CC-021). Khoảng thời gian bắt buộc khi không lọc theo bản ghi (G3).
 */
@Service
public class AuditQueryService {

    public static final Duration DEFAULT_WINDOW = Duration.ofHours(24);
    public static final Duration MAX_WINDOW = Duration.ofDays(31);
    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 200;
    public static final int MAX_EXPORT_ROWS = 50_000;
    private static final String CURSOR_SEPARATOR = "|";
    private static final List<String> EXPORT_HEADERS = List.of("Thời điểm", "Phân hệ", "Loại đối tượng",
            "Mã đối tượng", "Hành động", "Thay đổi", "Lý do", "Người thực hiện", "Người duyệt", "Thiết bị", "IP");

    public record Criteria(String module, String entityType, String entityId, String action, UUID byUserId,
                           Instant from, Instant to) {

        boolean isEntityHistory() {
            return entityType != null && entityId != null;
        }
    }

    public record AuditRow(String id, Instant occurredAt, String module, String entityType, String entityId,
                           String action, List<FieldChange> fieldChanges, String reason, String byUserId,
                           String approvedByUserId, String deviceId, String ip, String adjustmentRequestId) {
    }

    public record AuditPage(List<AuditRow> items, String nextCursor) {
    }

    private record Cursor(Instant occurredAt, String id) {
    }

    private static final String SELECT_COLUMNS = "SELECT BIN_TO_UUID(id) AS id, occurred_at, module, entity_type, "
            + "entity_id, action, CAST(field_changes AS CHAR) AS field_changes, reason, "
            + "BIN_TO_UUID(by_user_id) AS by_user_id, BIN_TO_UUID(approved_by_user_id) AS approved_by_user_id, "
            + "BIN_TO_UUID(device_id) AS device_id, ip, BIN_TO_UUID(adjustment_request_id) AS adjustment_request_id "
            + "FROM sys_audit_log WHERE 1 = 1";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ExportService exports;
    private final AuditLogWriter audit;
    private final Clock clock;

    public AuditQueryService(JdbcTemplate jdbc, ObjectMapper mapper, ExportService exports, AuditLogWriter audit,
                             Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.exports = exports;
        this.audit = audit;
        this.clock = clock;
    }

    public AuditPage search(Criteria criteria, String cursor, int size) {
        Criteria resolved = resolveWindow(criteria);
        int pageSize = clampPageSize(size);
        Cursor after = decodeCursor(cursor);
        List<AuditRow> rows = fetch(resolved, after, pageSize + 1);
        boolean hasMore = rows.size() > pageSize;
        List<AuditRow> items = hasMore ? rows.subList(0, pageSize) : rows;
        String next = hasMore ? encodeCursor(items.get(items.size() - 1)) : null;
        return new AuditPage(items, next);
    }

    @Transactional
    public byte[] export(Criteria criteria, UUID actorId) {
        Criteria resolved = resolveWindow(criteria);
        List<AuditRow> rows = fetch(resolved, null, MAX_EXPORT_ROWS);
        audit.write(new AuditLogWriter.Entry("identity", "AuditLog", null, "EXPORT",
                List.of(new FieldChange("rows", null, rows.size())),
                "Xuất nhật ký kiểm toán", actorId, null, null, null));
        return exports.workbook("Nhật ký", EXPORT_HEADERS, rows.stream().map(this::exportRow).toList());
    }

    private Criteria resolveWindow(Criteria criteria) {
        if (criteria.isEntityHistory()) {
            return criteria;
        }
        Instant to = criteria.to() == null ? clock.instant() : criteria.to();
        Instant from = criteria.from() == null ? to.minus(DEFAULT_WINDOW) : criteria.from();
        if (!from.isBefore(to)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Thời điểm kết thúc phải sau thời điểm bắt đầu");
        }
        if (Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION, "Khoảng thời gian tra cứu tối đa 31 ngày");
        }
        return new Criteria(criteria.module(), criteria.entityType(), null, criteria.action(), criteria.byUserId(),
                from, to);
    }

    private List<AuditRow> fetch(Criteria c, Cursor after, int limit) {
        StringBuilder sql = new StringBuilder(SELECT_COLUMNS);
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, c);
        if (after != null) {
            sql.append(" AND (occurred_at < ? OR (occurred_at = ? AND id < UUID_TO_BIN(?)))");
            Timestamp ts = Timestamp.from(after.occurredAt());
            args.add(ts);
            args.add(ts);
            args.add(after.id());
        }
        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT ?");
        args.add(limit);
        return jdbc.query(sql.toString(), (rs, n) -> new AuditRow(rs.getString("id"),
                rs.getTimestamp("occurred_at").toInstant(), rs.getString("module"), rs.getString("entity_type"),
                rs.getString("entity_id"), rs.getString("action"), mask(rs.getString("field_changes")),
                rs.getString("reason"), rs.getString("by_user_id"), rs.getString("approved_by_user_id"),
                rs.getString("device_id"), rs.getString("ip"), rs.getString("adjustment_request_id")), args.toArray());
    }

    private static void appendFilters(StringBuilder sql, List<Object> args, Criteria c) {
        if (c.module() != null) {
            sql.append(" AND module = ?");
            args.add(c.module());
        }
        if (c.entityType() != null) {
            sql.append(" AND entity_type = ?");
            args.add(c.entityType());
        }
        if (c.entityId() != null) {
            sql.append(" AND entity_id = ?");
            args.add(c.entityId());
        }
        if (c.action() != null) {
            sql.append(" AND action = ?");
            args.add(c.action());
        }
        if (c.byUserId() != null) {
            sql.append(" AND by_user_id = UUID_TO_BIN(?)");
            args.add(c.byUserId().toString());
        }
        if (c.from() != null) {
            sql.append(" AND occurred_at >= ?");
            args.add(Timestamp.from(c.from()));
        }
        if (c.to() != null) {
            sql.append(" AND occurred_at < ?");
            args.add(Timestamp.from(c.to()));
        }
    }

    private List<FieldChange> mask(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return AuditFieldMasker.mask(mapper.readValue(json, new TypeReference<List<FieldChange>>() {
            }));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("field_changes của sys_audit_log không đọc được", ex);
        }
    }

    private static int clampPageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static String encodeCursor(AuditRow last) {
        String raw = last.occurredAt() + CURSOR_SEPARATOR + last.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int split = raw.indexOf(CURSOR_SEPARATOR);
            return new Cursor(Instant.parse(raw.substring(0, split)),
                    UUID.fromString(raw.substring(split + 1)).toString());
        } catch (IllegalArgumentException | DateTimeException | StringIndexOutOfBoundsException ex) {
            throw new BusinessException(ErrorCode.VALIDATION, "Con trỏ phân trang không hợp lệ");
        }
    }

    private List<Object> exportRow(AuditRow r) {
        String changes = r.fieldChanges().stream()
                .map(c -> c.field() + ": " + c.oldValue() + " → " + c.newValue())
                .reduce((a, b) -> a + "; " + b).orElse("");
        return List.of(r.occurredAt().toString(), r.module(), r.entityType(), nullToEmpty(r.entityId()), r.action(),
                changes, nullToEmpty(r.reason()), nullToEmpty(r.byUserId()), nullToEmpty(r.approvedByUserId()),
                nullToEmpty(r.deviceId()), nullToEmpty(r.ip()));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
