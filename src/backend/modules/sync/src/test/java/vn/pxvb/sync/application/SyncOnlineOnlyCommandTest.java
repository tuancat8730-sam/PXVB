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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vn.pxvb.alerting.contract.AlertRaiser;
import vn.pxvb.common.setting.SettingService;
import vn.pxvb.identity.contract.DeviceRegistry;
import vn.pxvb.sync.contract.SyncCommandHandler;
import vn.pxvb.sync.contract.SyncCommandHandler.Command;

/** Thao tác bắt buộc online (FR-CC-069): lệnh từ tablet bị từ chối trước khi tới handler. */
class SyncOnlineOnlyCommandTest {

    private static final Instant NOW = Instant.parse("2026-11-03T08:00:00Z");
    private static final String ONLINE_ONLY_TYPE = "SHIFT.Close";
    private static final String OFFLINE_TYPE = "PR.RecordOutput";

    @Test
    void rejectsOnlineOnlyCommandBeforeHandler() {
        // Arrange
        SyncCommandHandler handler = handlerFor(ONLINE_ONLY_TYPE);
        SyncService service = service(handler);
        SyncService.CommandInput input = input(ONLINE_ONLY_TYPE);

        // Act
        SyncService.PushResponse response = service.push(UUID.randomUUID(), UUID.randomUUID(), request(input));

        // Assert
        SyncService.CommandResult result = response.results().get(0);
        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.message()).contains("bắt buộc online");
        verify(handler, never()).apply(any(), anyBoolean());
    }

    @Test
    void offlineCommandReachesHandler() {
        // Arrange
        SyncCommandHandler handler = handlerFor(OFFLINE_TYPE);
        SyncService service = service(handler);
        SyncService.CommandInput input = input(OFFLINE_TYPE);

        // Act
        SyncService.PushResponse response = service.push(UUID.randomUUID(), UUID.randomUUID(), request(input));

        // Assert
        assertThat(response.results().get(0).status()).isEqualTo("APPLIED");
        verify(handler).apply(any(Command.class), anyBoolean());
    }

    private static boolean anyBoolean() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }

    private static SyncCommandHandler handlerFor(String type) {
        SyncCommandHandler handler = mock(SyncCommandHandler.class);
        when(handler.type()).thenReturn(type);
        return handler;
    }

    private static SyncService.CommandInput input(String type) {
        return new SyncService.CommandInput(UUID.randomUUID(), type, UUID.randomUUID(), null, NOW, NOW, 0L,
                UUID.randomUUID(), UUID.randomUUID());
    }

    private static SyncService.PushRequest request(SyncService.CommandInput input) {
        return new SyncService.PushRequest(NOW, "1.0.3", 1, List.of(input));
    }

    private static SyncService service(SyncCommandHandler handler) {
        SettingService settings = mock(SettingService.class);
        when(settings.getInt(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        return new SyncService(List.of(handler), List.of(), mock(JdbcTemplate.class),
                mock(PlatformTransactionManager.class), new ObjectMapper(), mock(DeviceRegistry.class),
                mock(AlertRaiser.class), settings, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
