package vn.pxvb.sync.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.pxvb.alerting.contract.AlertRaiser;
import vn.pxvb.alerting.contract.AlertRaiser.AlertCommand;
import vn.pxvb.common.setting.SettingService;
import vn.pxvb.identity.contract.DeviceRegistry;

/**
 * Cảnh báo thiết bị còn lệnh chưa đồng bộ quá ngưỡng {@code SYNC_STALE_MIN} (mặc định 30', rule
 * {@code CC_DEVICE_UNSYNCED}), và hàng đợi quá {@code SYNC_QUEUE_MAX} lệnh (mặc định 500, rule
 * {@code CC_QUEUE_BACKLOG}, FR-CC-068). Chạy mỗi phút khi {@code pxvb.sync.scanner.enabled}.
 */
@Component
public class SyncMonitorScanner {

    private static final int DEFAULT_STALE_MINUTES = 30;
    private static final int DEFAULT_QUEUE_MAX = 500;
    private static final String QUEUE_RULE_CODE = "CC_QUEUE_BACKLOG";

    private final DeviceRegistry registry;
    private final AlertRaiser alerts;
    private final SettingService settings;
    private final Clock clock;
    private final boolean enabled;

    public SyncMonitorScanner(DeviceRegistry registry, AlertRaiser alerts, SettingService settings, Clock clock,
                              @Value("${pxvb.sync.scanner.enabled:false}") boolean enabled) {
        this.registry = registry;
        this.alerts = alerts;
        this.settings = settings;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${pxvb.sync.scanner.interval-ms:60000}")
    public void scheduled() {
        if (enabled) {
            scan(clock.instant());
            scanQueueBacklog();
        }
    }

    /** @return số thiết bị hoạt động có hàng đợi vượt {@code SYNC_QUEUE_MAX}. */
    public int scanQueueBacklog() {
        int max = settings.getInt("SYNC_QUEUE_MAX", DEFAULT_QUEUE_MAX);
        int count = 0;
        for (DeviceRegistry.DeviceInfo d : registry.devices()) {
            if (d.active() && d.pendingCount() > max) {
                alerts.raise(new AlertCommand(QUEUE_RULE_CODE, "Device", d.id().toString(), null, null,
                        "Thiết bị " + d.name() + " còn " + d.pendingCount() + " lệnh chờ đồng bộ",
                        "Vượt ngưỡng " + max + " lệnh", Map.of("deviceCode", d.code(), "pending", d.pendingCount()),
                        null, null));
                count++;
            }
        }
        return count;
    }

    /** @return số thiết bị bị cảnh báo. */
    public int scan(Instant now) {
        int stale = settings.getInt("SYNC_STALE_MIN", DEFAULT_STALE_MINUTES);
        Instant limit = now.minus(Duration.ofMinutes(stale));
        int count = 0;
        for (DeviceRegistry.DeviceInfo d : registry.devices()) {
            Instant last = d.lastSyncAt() != null ? d.lastSyncAt() : d.lastSeenAt();
            if (d.active() && d.pendingCount() > 0 && last != null && last.isBefore(limit)) {
                alerts.raise(new AlertCommand("CC_DEVICE_UNSYNCED", "Device", d.id().toString(), null, null,
                        "Thiết bị " + d.name() + " chưa đồng bộ " + d.pendingCount() + " lệnh",
                        "Lần đồng bộ cuối: " + last, Map.of("deviceCode", d.code(), "pending", d.pendingCount()),
                        null, null));
                count++;
            }
        }
        return count;
    }
}
