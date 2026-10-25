package vn.pxvb.alerting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import vn.pxvb.alerting.application.Repositories.AlertRepository;
import vn.pxvb.alerting.application.Repositories.HistoryRepository;
import vn.pxvb.alerting.application.Repositories.RecipientRepository;
import vn.pxvb.alerting.application.Repositories.RuleRepository;
import vn.pxvb.alerting.domain.Alert;
import vn.pxvb.alerting.domain.AlertRule;
import vn.pxvb.alerting.infrastructure.AlertNotifyMetrics;
import vn.pxvb.alerting.infrastructure.RealtimePublisher;
import vn.pxvb.dataio.application.ExportService;
import vn.pxvb.identity.contract.IdentityDirectory;
import vn.pxvb.masterdata.contract.MasterdataQueries;

/** Quét leo thang (1.4): tải rule một lần cho cả lượt quét, không truy vấn rule theo từng cảnh báo. */
class AlertServiceEscalationTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
    private static final String RULE_CODE = "PR_OUTPUT_BEHIND";
    private static final String ESCALATION_POLICY = "{\"levels\":[{\"afterMinutes\":30,\"roles\":[\"DEPUTY_DIRECTOR\"]}]}";
    private static final int ALERT_COUNT = 3;

    @Test
    void escalateDueLoadsRulesOnceInsteadOfPerAlert() {
        // Arrange
        RuleRepository rules = mock(RuleRepository.class);
        AlertRepository alerts = mock(AlertRepository.class);
        AlertRule rule = new AlertRule(RULE_CODE, "PR", RULE_CODE, Alert.Severity.WARN, "{}", ESCALATION_POLICY, 10);
        when(rules.findAll()).thenReturn(List.of(rule));
        when(rules.findByCode(anyString())).thenReturn(Optional.of(rule));
        when(alerts.findByStatus(Alert.Status.OPEN)).thenReturn(List.of(
                openAlert(), openAlert(), openAlert()));
        AlertService service = service(rules, alerts);

        // Act
        int escalated = service.escalateDue(NOW.plus(31, ChronoUnit.MINUTES));

        // Assert
        assertThat(escalated).isEqualTo(ALERT_COUNT);
        verify(rules, times(1)).findAll();
        verify(rules, never()).findByCode(anyString());
    }

    private static Alert openAlert() {
        return new Alert(RULE_CODE, Alert.Severity.WARN, "PR", "Machine", "M-" + Math.random(), null, null, null,
                "Cảnh báo", "Chi tiết", "{}", "key-" + Math.random(), NOW, 1, "{}");
    }

    private static AlertService service(RuleRepository rules, AlertRepository alerts) {
        return new AlertService(rules, alerts, mock(HistoryRepository.class), mock(RecipientRepository.class),
                mock(IdentityDirectory.class), mock(NotificationService.class), mock(RealtimePublisher.class),
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC), mock(MasterdataQueries.class),
                mock(EmailService.class), mock(ExportService.class), mock(AlertNotifyMetrics.class));
    }
}
