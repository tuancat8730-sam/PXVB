package vn.pxvb.sync.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vn.pxvb.alerting.contract.AlertRaiser;
import vn.pxvb.alerting.contract.AlertRaiser.AlertCommand;
import vn.pxvb.common.setting.SettingService;
import vn.pxvb.identity.contract.DeviceRegistry;

/** Lệch đồng hồ tablet (FR-CC-067): vượt {@code SYNC_CLOCK_SKEW_SEC} (mặc định 120 giây) thì cờ và cảnh báo CC_CLOCK_SKEW. */
class SyncServiceClockSkewTest {

    private static final Instant NOW = Instant.parse("2026-11-03T08:00:00Z");
    private static final String RULE_CODE = "CC_CLOCK_SKEW";

    @Test
    void warnsAndRaisesAlertWhenDeviceClockIsBeyondDefaultLimit() {
        // Arrange
        AlertRaiser alerts = mock(AlertRaiser.class);
        SyncService service = service(alerts, defaultSettings());

        // Act
        SyncService.PushResponse response = service.push(UUID.randomUUID(), UUID.randomUUID(),
                request(NOW.minus(Duration.ofMinutes(3))));

        // Assert
        assertThat(response.clockSkewWarning()).isTrue();
        ArgumentCaptor<AlertCommand> command = ArgumentCaptor.forClass(AlertCommand.class);
        verify(alerts).raise(command.capture());
        assertThat(command.getValue().ruleCode()).isEqualTo(RULE_CODE);
    }

    @Test
    void doesNotWarnWhenDeviceClockIsWithinLimit() {
        // Arrange
        AlertRaiser alerts = mock(AlertRaiser.class);
        SyncService service = service(alerts, defaultSettings());

        // Act
        SyncService.PushResponse response = service.push(UUID.randomUUID(), UUID.randomUUID(),
                request(NOW.minus(Duration.ofSeconds(60))));

        // Assert
        assertThat(response.clockSkewWarning()).isFalse();
        verify(alerts, never()).raise(any());
    }

    @Test
    void usesConfiguredLimitFromSettings() {
        // Arrange
        int configuredSeconds = 30;
        AlertRaiser alerts = mock(AlertRaiser.class);
        SettingService settings = mock(SettingService.class);
        when(settings.getInt("SYNC_CLOCK_SKEW_SEC", 120)).thenReturn(configuredSeconds);
        SyncService service = service(alerts, settings);

        // Act
        SyncService.PushResponse response = service.push(UUID.randomUUID(), UUID.randomUUID(),
                request(NOW.minus(Duration.ofSeconds(45))));

        // Assert
        assertThat(response.clockSkewWarning()).isTrue();
        verify(alerts).raise(any());
    }

    private static SettingService defaultSettings() {
        SettingService settings = mock(SettingService.class);
        when(settings.getInt(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        return settings;
    }

    private static SyncService.PushRequest request(Instant deviceTime) {
        return new SyncService.PushRequest(deviceTime, "1.0.3", 0, List.of());
    }

    private static SyncService service(AlertRaiser alerts, SettingService settings) {
        DeviceRegistry registry = mock(DeviceRegistry.class);
        return new SyncService(List.of(), List.of(), mock(JdbcTemplate.class), mock(PlatformTransactionManager.class),
                new ObjectMapper(), registry, alerts, settings, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
