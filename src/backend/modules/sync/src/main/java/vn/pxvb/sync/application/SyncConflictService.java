package vn.pxvb.sync.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.audit.AuditLogWriter;
import vn.pxvb.common.audit.AuditLogWriter.FieldChange;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.sync.contract.SyncCommandHandler;
import vn.pxvb.sync.contract.SyncCommandHandler.Command;

/**
 * Xử lý xung đột đồng bộ (FR-CC-063): người có thẩm quyền chọn chấp nhận bản tablet (áp lại lệnh với {@code force}),
 * giữ bản máy chủ, hoặc hủy. Mọi quyết định được audit.
 */
@Service
public class SyncConflictService {

    public enum Decision { ACCEPT_CLIENT, KEEP_SERVER, DISCARD }

    private final Map<String, SyncCommandHandler> handlers = new HashMap<>();
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditLogWriter audit;
    private final Clock clock;

    public SyncConflictService(List<SyncCommandHandler> handlerBeans, JdbcTemplate jdbc, ObjectMapper mapper,
                               AuditLogWriter audit, Clock clock) {
        handlerBeans.forEach(h -> handlers.put(h.type(), h));
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String status) {
        String where = status == null || status.isBlank() ? "" : " WHERE status = ?";
        String sql = "SELECT BIN_TO_UUID(id) AS id, BIN_TO_UUID(device_id) AS deviceId, BIN_TO_UUID(command_id) AS "
                + "commandId, command_type AS commandType, BIN_TO_UUID(entity_id) AS entityId, reason, status, "
                + "created_at AS createdAt, resolved_at AS resolvedAt FROM sys_sync_conflict" + where
                + " ORDER BY created_at DESC LIMIT 500";
        return status == null || status.isBlank() ? jdbc.queryForList(sql) : jdbc.queryForList(sql, status);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, BIN_TO_UUID(device_id) AS "
                + "deviceId, BIN_TO_UUID(command_id) AS commandId, command_type AS commandType, "
                + "BIN_TO_UUID(entity_id) AS entityId, CAST(client_payload AS CHAR) AS clientPayload, "
                + "CAST(server_snapshot AS CHAR) AS serverSnapshot, reason, status, created_at AS createdAt, "
                + "resolved_at AS resolvedAt, resolution_note AS resolutionNote FROM sys_sync_conflict "
                + "WHERE id = UUID_TO_BIN(?)", id.toString());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy xung đột");
        }
        return rows.get(0);
    }

    @Transactional
    public Map<String, Object> resolve(UUID id, Decision decision, String note, UUID userId) {
        Map<String, Object> conflict = get(id);
        if (!"OPEN".equals(conflict.get("status"))) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Xung đột đã được xử lý");
        }
        String newStatus = switch (decision) {
            case ACCEPT_CLIENT -> "RESOLVED_ACCEPT_CLIENT";
            case KEEP_SERVER -> "RESOLVED_KEEP_SERVER";
            case DISCARD -> "DISCARDED";
        };
        if (decision == Decision.ACCEPT_CLIENT) {
            applyClientCommand(conflict);
        }
        jdbc.update("UPDATE sys_sync_conflict SET status = ?, resolved_by = UUID_TO_BIN(?), resolved_at = "
                        + "UTC_TIMESTAMP(6), resolution_note = ?, row_version = row_version + 1 "
                        + "WHERE id = UUID_TO_BIN(?)", newStatus, userId.toString(), note, id.toString());
        audit.write(new AuditLogWriter.Entry("sync", "SyncConflict", id.toString(), "RESOLVE",
                List.of(new FieldChange("status", "OPEN", newStatus)), note, userId, null, null, null));
        return get(id);
    }

    private void applyClientCommand(Map<String, Object> conflict) {
        try {
            JsonNode payload = mapper.readTree((String) conflict.get("clientPayload"));
            Command command = mapper.treeToValue(payload, Command.class);
            SyncCommandHandler handler = handlers.get(command.type());
            if (handler == null) {
                throw new BusinessException(ErrorCode.VALIDATION, "Không có bộ xử lý cho " + command.type());
            }
            JsonNode result = handler.apply(command, true);
            jdbc.update("UPDATE sys_sync_command SET status = 'APPLIED', message = 'Chấp nhận bản tablet', "
                            + "result = CAST(? AS JSON) WHERE device_id = UUID_TO_BIN(?) AND command_id = UUID_TO_BIN(?)",
                    result == null ? null : result.toString(), conflict.get("deviceId"), conflict.get("commandId"));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL, "Không đọc được nội dung lệnh xung đột");
        }
    }
}
