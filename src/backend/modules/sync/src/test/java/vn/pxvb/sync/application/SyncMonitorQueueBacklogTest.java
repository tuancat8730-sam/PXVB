package vn.pxvb.sync.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.pxvb.alerting.contract.AlertRaiser;
import vn.pxvb.alerting.contract.AlertRaiser.AlertCommand;
import vn.pxvb.common.setting.SettingService;
import vn.pxvb.identity.contract.DeviceRegistry;
import vn.pxvb.identity.contract.DeviceRegistry.DeviceInfo;

/** Cảnh báo hàng đợi quá lớn (FR-CC-068): ngưỡng mặc định 500 lệnh, cấu hình được qua SYNC_QUEUE_MAX. */
class SyncMonitorQueueBacklogTest {

    private static final Instant NOW = Instant.parse("2026-11-03T08:00:00Z");
    private static final int DEFAULT_QUEUE_MAX = 500;
    private static final String RULE_CODE = "CC_QUEUE_BACKLOG";

    @Test
    void raisesAlertWhenPendingExceedsDefaultLimit() {
        // Arrange
        AlertRaiser alerts = mock(AlertRaiser.class);
        DeviceRegistry registry = registryWith(device(DEFAULT_QUEUE_MAX + 1, true));
        SyncMonitorScanner scanner = scanner(registry, alerts, defaultSettings());

        // Act
        int raised = scanner.scanQueueBacklog();

        // Assert
        assertThat(raised).isEqualTo(1);
        ArgumentCaptor<AlertCommand> command = ArgumentCaptor.forClass(AlertCommand.class);
        verify(alerts).raise(command.capture());
        assertThat(command.getValue().ruleCode()).isEqualTo(RULE_CODE);
        assertThat(command.getValue().payload()).containsEntry("pending", DEFAULT_QUEUE_MAX + 1);
    }

    @Test
    void doesNotRaiseWhenPendingEqualsLimit() {
        // Arrange
        AlertRaiser alerts = mock(AlertRaiser.class);
        DeviceRegistry registry = registryWith(device(DEFAULT_QUEUE_MAX, true));
        SyncMonitorScanner scanner = scanner(registry, alerts, defaultSettings());

        // Act
        int raised = scanner.scanQueueBacklog();

        // Assert
        assertThat(raised).isZero();
        verify(alerts, never()).raise(any());
    }

    @Test
    void skipsInactiveDevice() {
        // Arrange
        AlertRaiser alerts = mock(AlertRaiser.class);
        DeviceRegistry registry = registryWith(device(DEFAULT_QUEUE_MAX * 2, false));
        SyncMonitorScanner scanner = scanner(registry, alerts, defaultSettings());

        // Act
        int raised = scanner.scanQueueBacklog();

        // Assert
        assertThat(raised).isZero();
        verify(alerts, never()).raise(any());
    }

    @Test
    void usesConfiguredLimitFromSettings() {
        // Arrange
        int configuredLimit = 10;
        AlertRaiser alerts = mock(AlertRaiser.class);
        SettingService settings = mock(SettingService.class);
        when(settings.getInt("SYNC_QUEUE_MAX", DEFAULT_QUEUE_MAX)).thenReturn(configuredLimit);
        DeviceRegistry registry = registryWith(device(configuredLimit + 1, true));
        SyncMonitorScanner scanner = scanner(registry, alerts, settings);

        // Act
        int raised = scanner.scanQueueBacklog();

        // Assert
        assertThat(raised).isEqualTo(1);
    }

    private static SettingService defaultSettings() {
        SettingService settings = mock(SettingService.class);
        when(settings.getInt(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        return settings;
    }

    private static DeviceRegistry registryWith(DeviceInfo... devices) {
        DeviceRegistry registry = mock(DeviceRegistry.class);
        when(registry.devices()).thenReturn(List.of(devices));
        return registry;
    }

    private static DeviceInfo device(int pendingCount, boolean active) {
        return new DeviceInfo(UUID.randomUUID(), "TAB-01", "Tablet 01", active, NOW, NOW, pendingCount, "1.0.3", 0L);
    }

    private static SyncMonitorScanner scanner(DeviceRegistry registry, AlertRaiser alerts, SettingService settings) {
        return new SyncMonitorScanner(registry, alerts, settings, Clock.fixed(NOW, ZoneOffset.UTC), true);
    }
}
