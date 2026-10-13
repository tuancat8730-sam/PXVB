package vn.pxvb.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Ghi {@code sys_audit_log} (append-only, FR-CC-020/021). Dùng JDBC trực tiếp trên cùng giao dịch nên bản ghi
 * audit commit/rollback cùng nghiệp vụ.
 */
@Component
public class AuditLogWriter {

    public record FieldChange(String field, Object oldValue, Object newValue) {
    }

    public record Entry(String module, String entityType, String entityId, String action,
                        List<FieldChange> changes, String reason, UUID byUserId, UUID approvedByUserId,
                        UUID deviceId, UUID adjustmentRequestId) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuditLogWriter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void write(Entry e) {
        String changesJson = null;
        if (e.changes() != null && !e.changes().isEmpty()) {
            try {
                changesJson = mapper.writeValueAsString(e.changes());
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Không serialize được FieldChanges", ex);
            }
        }
        jdbc.update("INSERT INTO sys_audit_log(id, occurred_at, module, entity_type, entity_id, action, "
                        + "field_changes, reason, by_user_id, approved_by_user_id, device_id, ip, "
                        + "adjustment_request_id) VALUES (UUID_TO_BIN(?), UTC_TIMESTAMP(6), ?, ?, ?, ?, ?, ?, "
                        + "UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, UUID_TO_BIN(?))",
                UuidCreator.getTimeOrderedEpoch().toString(), e.module(), e.entityType(), e.entityId(),
                e.action(), changesJson, e.reason(), str(e.byUserId()), str(e.approvedByUserId()),
                str(e.deviceId()), currentIp(), str(e.adjustmentRequestId()));
    }

    private static String str(UUID id) {
        return id == null ? null : id.toString();
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return null;
    }
}
