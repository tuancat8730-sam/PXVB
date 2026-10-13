package vn.pxvb.sync.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.common.security.PxvbPrincipal;
import vn.pxvb.identity.contract.DeviceRegistry;
import vn.pxvb.sync.application.SyncConflictService;
import vn.pxvb.sync.application.SyncConflictService.Decision;
import vn.pxvb.sync.application.SyncService;
import vn.pxvb.sync.application.SyncService.Manifest;
import vn.pxvb.sync.application.SyncService.PullResponse;
import vn.pxvb.sync.application.SyncService.PushRequest;
import vn.pxvb.sync.application.SyncService.PushResponse;

/** API đồng bộ cho tablet và giám sát (FR-CC-061/062/063/066). */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    public record ResolveRequest(Decision decision, String note) {
    }

    private final SyncService sync;
    private final SyncConflictService conflicts;
    private final DeviceRegistry registry;
    private final JdbcTemplate jdbc;
    private final CurrentUser currentUser;

    public SyncController(SyncService sync, SyncConflictService conflicts, DeviceRegistry registry,
                          JdbcTemplate jdbc, CurrentUser currentUser) {
        this.sync = sync;
        this.conflicts = conflicts;
        this.registry = registry;
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    @PostMapping("/push")
    @PreAuthorize("hasAuthority('sync.push')")
    public PushResponse push(@RequestBody PushRequest request) {
        PxvbPrincipal p = currentUser.require();
        if (p.deviceId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION,
                    "Cần đăng nhập trên thiết bị đã kích hoạt để đồng bộ");
        }
        return sync.push(p.userId(), p.deviceId(), request);
    }

    @GetMapping("/pull/manifest")
    @PreAuthorize("hasAuthority('sync.pull')")
    public Manifest manifest() {
        return sync.manifest();
    }

    /** {@code since} (ISO-8601) bỏ trống = tải đầy đủ; lặp lại với {@code cursor=nextCursor} đến khi null. */
    @GetMapping("/pull")
    @PreAuthorize("hasAuthority('sync.pull')")
    public PullResponse pull(@RequestParam String resource, @RequestParam(required = false) Instant since,
                             @RequestParam(required = false) String cursor,
                             @RequestParam(defaultValue = "500") int limit) {
        return sync.pull(resource, since, cursor, limit);
    }

    @GetMapping("/conflicts")
    @PreAuthorize("hasAuthority('sync.conflict.view')")
    public List<Map<String, Object>> conflicts(@RequestParam(required = false) String status) {
        return conflicts.list(status);
    }

    @GetMapping("/conflicts/{id}")
    @PreAuthorize("hasAuthority('sync.conflict.view')")
    public Map<String, Object> conflict(@PathVariable UUID id) {
        return conflicts.get(id);
    }

    @PostMapping("/conflicts/{id}/resolve")
    @PreAuthorize("hasAuthority('sync.conflict.resolve')")
    public Map<String, Object> resolve(@PathVariable UUID id, @RequestBody ResolveRequest request) {
        if (request.decision() == null) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cần chọn cách xử lý xung đột");
        }
        return conflicts.resolve(id, request.decision(), request.note(), currentUser.require().userId());
    }

    /** Giám sát thiết bị & đồng bộ (FR-CC-066). */
    @GetMapping("/devices")
    @PreAuthorize("hasAuthority('sync.device.view')")
    public List<Map<String, Object>> devices() {
        Map<UUID, Long> openConflicts = new java.util.HashMap<>();
        jdbc.query("SELECT BIN_TO_UUID(device_id) AS d, COUNT(*) AS n FROM sys_sync_conflict WHERE status = 'OPEN' "
                + "GROUP BY device_id", rs -> {
            openConflicts.put(UUID.fromString(rs.getString("d")), rs.getLong("n"));
        });
        return registry.devices().stream().map(d -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", d.id());
            m.put("deviceCode", d.code());
            m.put("name", d.name());
            m.put("active", d.active());
            m.put("lastSeenAt", d.lastSeenAt());
            m.put("lastSyncAt", d.lastSyncAt());
            m.put("pendingCount", d.pendingCount());
            m.put("appVersion", d.appVersion());
            m.put("clockOffsetMs", d.clockOffsetMs());
            m.put("openConflicts", openConflicts.getOrDefault(d.id(), 0L));
            return m;
        }).toList();
    }
}
