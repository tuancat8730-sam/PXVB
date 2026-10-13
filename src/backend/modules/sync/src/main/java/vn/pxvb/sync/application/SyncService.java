package vn.pxvb.sync.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.pxvb.alerting.contract.AlertRaiser;
import vn.pxvb.alerting.contract.AlertRaiser.AlertCommand;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.identity.contract.DeviceRegistry;
import vn.pxvb.sync.contract.SyncCommandHandler;
import vn.pxvb.sync.contract.SyncCommandHandler.Command;
import vn.pxvb.sync.contract.SyncCommandHandler.ConflictException;
import vn.pxvb.sync.contract.SyncCommandHandler.RejectedException;
import vn.pxvb.common.sync.SyncPullProvider;

/**
 * Đồng bộ ngoại tuyến (CC-07, FR-CC-060..069): nhận lô lệnh theo thứ tự, idempotent theo
 * {@code (deviceId, clientCommandId)}, không bao giờ ghi đè khi xung đột; cung cấp dữ liệu delta cho tablet.
 */
@Service
public class SyncService {

    public static final int MAX_BATCH = 100;
    public static final long CLOCK_SKEW_WARN_MS = 120_000;
    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    public record CommandInput(UUID clientCommandId, String type, UUID entityId, JsonNode payload,
                               Instant occurredAt, Instant enteredAt, Long baseVersion, UUID shiftSessionId,
                               UUID machineId) {
    }

    public record PushRequest(Instant deviceTime, String appVersion, Integer pendingCount,
                              List<CommandInput> commands) {
    }

    /** {@code status} ∈ APPLIED | DUPLICATE | REJECTED | CONFLICT; {@code retryable} khi lỗi tạm thời. */
    public record CommandResult(UUID clientCommandId, String status, String message, UUID conflictId,
                                JsonNode data, boolean retryable) {
    }

    public record PushResponse(List<CommandResult> results, Instant serverTime, Long clockOffsetMs,
                               boolean clockSkewWarning) {
    }

    public record Manifest(Instant serverTime, List<String> resources, List<String> fullResources) {
    }

    public record PullResponse(String resource, List<Map<String, Object>> records, String nextCursor,
                               Instant serverTime) {
    }

    private final Map<String, SyncCommandHandler> handlers = new HashMap<>();
    private final Map<String, SyncPullProvider> providers = new HashMap<>();
    private final List<String> resourceOrder = new ArrayList<>();
    private final List<String> fullResources = new ArrayList<>();
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final DeviceRegistry registry;
    private final AlertRaiser alerts;
    private final Clock clock;

    public SyncService(List<SyncCommandHandler> handlerBeans, List<SyncPullProvider> providerBeans,
                       JdbcTemplate jdbc, PlatformTransactionManager txManager, ObjectMapper mapper,
                       DeviceRegistry registry, AlertRaiser alerts, Clock clock) {
        handlerBeans.forEach(h -> handlers.put(h.type(), h));
        providerBeans.forEach(p -> p.resources().forEach(r -> {
            providers.put(r, p);
            resourceOrder.add(r);
            if (p.alwaysFull().contains(r)) {
                fullResources.add(r);
            }
        }));
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.mapper = mapper;
        this.registry = registry;
        this.alerts = alerts;
        this.clock = clock;
    }

    // ---------- Push ----------

    public PushResponse push(UUID userId, UUID deviceId, PushRequest request) {
        List<CommandInput> commands = request.commands() == null ? List.of() : request.commands();
        if (commands.size() > MAX_BATCH) {
            throw new BusinessException(ErrorCode.VALIDATION, "Mỗi lô tối đa " + MAX_BATCH + " lệnh");
        }
        Instant received = clock.instant();
        Long offsetMs = request.deviceTime() == null ? null
                : Duration.between(request.deviceTime(), received).toMillis();
        List<CommandResult> results = new ArrayList<>();
        for (CommandInput in : commands) {
            results.add(processOne(userId, deviceId, in, received));
        }
        registry.recordSync(deviceId, request.pendingCount() == null ? 0 : request.pendingCount(), offsetMs,
                request.appVersion());
        return new PushResponse(results, received, offsetMs,
                offsetMs != null && Math.abs(offsetMs) > CLOCK_SKEW_WARN_MS);
    }

    private CommandResult processOne(UUID userId, UUID deviceId, CommandInput in, Instant received) {
        if (in.clientCommandId() == null || in.type() == null || in.type().isBlank()) {
            return new CommandResult(in.clientCommandId(), "REJECTED", "Thiếu clientCommandId hoặc type", null,
                    null, false);
        }
        List<Map<String, Object>> stored = jdbc.queryForList("SELECT status, message, BIN_TO_UUID(conflict_id) AS "
                + "conflictId FROM sys_sync_command WHERE device_id = UUID_TO_BIN(?) AND command_id = UUID_TO_BIN(?)",
                deviceId.toString(), in.clientCommandId().toString());
        if (!stored.isEmpty()) {
            Map<String, Object> row = stored.get(0);
            String conflict = (String) row.get("conflictId");
            return new CommandResult(in.clientCommandId(), "DUPLICATE",
                    "Đã xử lý trước đó: " + row.get("status"), conflict == null ? null : UUID.fromString(conflict),
                    null, false);
        }
        SyncCommandHandler handler = handlers.get(in.type());
        if (handler == null) {
            record(deviceId, userId, in, "REJECTED", "Loại lệnh không được hỗ trợ: " + in.type(), null, null,
                    received);
            return new CommandResult(in.clientCommandId(), "REJECTED", "Loại lệnh không được hỗ trợ: " + in.type(),
                    null, null, false);
        }
        Command command = new Command(in.clientCommandId(), in.type(), in.entityId(), in.payload(),
                in.occurredAt(), in.enteredAt(), userId, deviceId, in.baseVersion(), in.shiftSessionId(),
                in.machineId());
        try {
            JsonNode data = tx.execute(status -> {
                JsonNode result = handler.apply(command, false);
                record(deviceId, userId, in, "APPLIED", null, result, null, received);
                return result;
            });
            return new CommandResult(in.clientCommandId(), "APPLIED", null, null, data, false);
        } catch (ConflictException e) {
            return conflict(deviceId, userId, in, command, e.getMessage(), e.serverSnapshot(), received);
        } catch (RejectedException e) {
            record(deviceId, userId, in, "REJECTED", e.getMessage(), null, null, received);
            return new CommandResult(in.clientCommandId(), "REJECTED", e.getMessage(), null, null, false);
        } catch (BusinessException e) {
            if (e.errorCode() == ErrorCode.DATA_LOCKED || e.errorCode() == ErrorCode.CONFLICT) {
                return conflict(deviceId, userId, in, command, e.getMessage(), null, received);
            }
            record(deviceId, userId, in, "REJECTED", e.getMessage(), null, null, received);
            return new CommandResult(in.clientCommandId(), "REJECTED", e.getMessage(), null, null, false);
        } catch (RuntimeException e) {
            log.error("Lệnh đồng bộ {} ({}) lỗi không dự kiến", in.clientCommandId(), in.type(), e);
            return new CommandResult(in.clientCommandId(), "REJECTED", "Lỗi hệ thống, sẽ thử lại", null, null, true);
        }
    }

    private CommandResult conflict(UUID deviceId, UUID userId, CommandInput in, Command command, String reason,
                                   JsonNode serverSnapshot, Instant received) {
        UUID conflictId = UuidCreator.getTimeOrderedEpoch();
        tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO sys_sync_conflict(id, device_id, command_id, command_type, entity_id, "
                            + "client_payload, server_snapshot, reason, status, created_at) VALUES (UUID_TO_BIN(?), "
                            + "UUID_TO_BIN(?), UUID_TO_BIN(?), ?, UUID_TO_BIN(?), CAST(? AS JSON), CAST(? AS JSON), "
                            + "?, 'OPEN', UTC_TIMESTAMP(6))", conflictId.toString(), deviceId.toString(),
                    in.clientCommandId().toString(), in.type(),
                    in.entityId() == null ? null : in.entityId().toString(), json(command),
                    serverSnapshot == null ? null : serverSnapshot.toString(), reason);
            record(deviceId, userId, in, "CONFLICT", reason, null, conflictId, received);
            alerts.raise(new AlertCommand("CC_SYNC_CONFLICT", "SyncConflict", conflictId.toString(), in.machineId(),
                    in.shiftSessionId(), "Xung đột đồng bộ: " + in.type(), reason, Map.of("deviceId",
                    deviceId.toString(), "commandId", in.clientCommandId().toString()),
                    "sync-conflict:" + conflictId, null));
        });
        return new CommandResult(in.clientCommandId(), "CONFLICT", reason, conflictId, null, false);
    }

    private void record(UUID deviceId, UUID userId, CommandInput in, String status, String message, JsonNode result,
                        UUID conflictId, Instant received) {
        Runnable insert = () -> jdbc.update("INSERT INTO sys_sync_command(device_id, command_id, type, entity_id, "
                        + "status, message, result, user_id, occurred_at, entered_at, received_at, conflict_id) "
                        + "VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, UUID_TO_BIN(?), ?, ?, CAST(? AS JSON), "
                        + "UUID_TO_BIN(?), ?, ?, ?, UUID_TO_BIN(?))", deviceId.toString(),
                in.clientCommandId().toString(), in.type(), in.entityId() == null ? null : in.entityId().toString(),
                status, message, result == null ? null : result.toString(), userId == null ? null : userId.toString(),
                in.occurredAt() == null ? null : java.sql.Timestamp.from(in.occurredAt()),
                in.enteredAt() == null ? null : java.sql.Timestamp.from(in.enteredAt()),
                java.sql.Timestamp.from(received), conflictId == null ? null : conflictId.toString());
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            insert.run();
        } else {
            tx.executeWithoutResult(s -> insert.run());
        }
    }

    // ---------- Pull ----------

    public Manifest manifest() {
        return new Manifest(clock.instant(), List.copyOf(resourceOrder), List.copyOf(fullResources));
    }

    public PullResponse pull(String resource, Instant since, String cursor, int limit) {
        SyncPullProvider provider = providers.get(resource);
        if (provider == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không có tài nguyên đồng bộ: " + resource);
        }
        int size = Math.min(Math.max(limit, 1), 1000);
        Instant serverTime = clock.instant();
        SyncPullProvider.Page page = provider.pull(resource, since, cursor, size);
        return new PullResponse(resource, page.records(), page.nextCursor(), serverTime);
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
