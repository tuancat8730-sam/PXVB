package vn.pxvb.alerting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.jpa.domain.Specification;
import vn.pxvb.alerting.application.Repositories.AlertRepository;
import vn.pxvb.alerting.application.Repositories.HistoryRepository;
import vn.pxvb.alerting.application.Repositories.RecipientRepository;
import vn.pxvb.alerting.application.Repositories.RuleRepository;
import vn.pxvb.alerting.contract.AlertRaiser.AlertCommand;
import vn.pxvb.alerting.contract.AlertRaiser.AlertRef;
import vn.pxvb.alerting.domain.Alert;
import vn.pxvb.alerting.domain.AlertRule;
import vn.pxvb.alerting.infrastructure.AlertNotifyMetrics;
import vn.pxvb.alerting.infrastructure.RealtimePublisher;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.common.security.DataScope;
import vn.pxvb.dataio.application.ExportService;
import vn.pxvb.identity.contract.IdentityDirectory;
import vn.pxvb.masterdata.contract.MasterdataQueries;

/** Nhánh logic của {@link AlertService} với repository mock: raise, phạm vi, thống kê, leo thang (E04 5.1). */
class AlertServiceUnitTest {

    private static final Instant NOW = Instant.parse("2026-10-25T03:00:00Z");
    private static final String RULE = "MT_WASTE_HIGH";
    private static final String POLICY = "{\"roles\":[\"SHIFT_LEADER\"]}";
    private static final String ESCALATION = "{\"levels\":[{\"afterMinutes\":30,\"roles\":[\"DEPUTY_DIRECTOR\"]}]}";
    private static final List<DataScope> ALL = List.of(DataScope.all());

    private final RuleRepository rules = mock(RuleRepository.class);
    private final AlertRepository alerts = mock(AlertRepository.class);

    @Test
    void raiseCreatesNewAlert() {
        when(rules.findByCode(RULE)).thenReturn(Optional.of(rule(true)));
        when(alerts.save(ArgumentMatchers.<Alert>any())).thenAnswer(inv -> inv.getArgument(0));

        AlertRef ref = service().raise(command("M-1"));

        assertThat(ref.created()).isTrue();
        assertThat(ref.alertId()).isNotNull();
    }

    @Test
    void raiseInsideDedupWindowRecursInsteadOfCreating() {
        when(rules.findByCode(RULE)).thenReturn(Optional.of(rule(true)));
        Alert existing = alert(Alert.Severity.WARN, null, NOW);
        when(alerts.findFirstByDedupKeyAndStatusInOrderByRaisedAtDesc(anyString(), ArgumentMatchers.any()))
                .thenReturn(Optional.of(existing));

        AlertRef ref = service().raise(command("M-1"));

        assertThat(ref.created()).isFalse();
        assertThat(existing.getOccurrenceCount()).isEqualTo(2);
    }

    @Test
    void raiseWithInactiveRuleCreatesNothing() {
        when(rules.findByCode(RULE)).thenReturn(Optional.of(rule(false)));

        AlertRef ref = service().raise(command("M-1"));

        assertThat(ref.alertId()).isNull();
        assertThat(ref.created()).isFalse();
    }

    @Test
    void acknowledgeOutsideLineScopeIsForbidden() {
        Alert alert = alert(Alert.Severity.WARN, "LINE-B", NOW);
        when(alerts.findById(alert.getId())).thenReturn(Optional.of(alert));
        List<DataScope> lineA = List.of(new DataScope(DataScope.Type.LINE, "LINE-A"));

        assertThatThrownBy(() -> service().acknowledge(alert.getId(), UUID.randomUUID(), "ok", lineA))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
        assertThat(alert.getStatus()).isEqualTo(Alert.Status.OPEN);
    }

    @Test
    void acknowledgeInsideLineScopeMovesToAck() {
        Alert alert = alert(Alert.Severity.WARN, "LINE-A", NOW);
        when(alerts.findById(alert.getId())).thenReturn(Optional.of(alert));
        List<DataScope> lineA = List.of(new DataScope(DataScope.Type.LINE, "LINE-A"));

        service().acknowledge(alert.getId(), UUID.randomUUID(), "Đã nhận", lineA);

        assertThat(alert.getStatus()).isEqualTo(Alert.Status.ACK);
    }

    @Test
    void detailOutsideScopeIsForbiddenButAllScopeSeesIt() {
        Alert alert = alert(Alert.Severity.WARN, null, NOW);
        when(alerts.findById(alert.getId())).thenReturn(Optional.of(alert));
        List<DataScope> lineA = List.of(new DataScope(DataScope.Type.LINE, "LINE-A"));
        AlertService service = service();

        assertThatThrownBy(() -> service.detail(alert.getId(), lineA))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
        assertThat(service.detail(alert.getId(), ALL).alert()).isSameAs(alert);
    }

    @Test
    void resolveWithoutCauseIsRejectedBeforeLoadingAlert() {
        assertThatThrownBy(() -> service().resolve(UUID.randomUUID(), UUID.randomUUID(), " ", "ghi chú", ALL))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION);
    }

    @Test
    void statsRejectReversedRangeAndRangeOverNinetyDays() {
        AlertService service = service();
        LocalDate from = LocalDate.of(2020, 1, 10);

        assertThatThrownBy(() -> service.stats(from, from.minusDays(1), ALL))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION);
        assertThatThrownBy(() -> service.stats(from, from.plusDays(90), ALL))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION);
    }

    @Test
    void statsGroupByRuleAndBusinessDateInVietnamTime() {
        // 17:30Z = 00:30 ngày 11/01 (Việt Nam); 16:30Z = 23:30 ngày 10/01.
        Alert late = alert(Alert.Severity.WARN, null, Instant.parse("2020-01-10T17:30:00Z"));
        Alert early = alert(Alert.Severity.WARN, null, Instant.parse("2020-01-10T16:30:00Z"));
        when(alerts.findAll(ArgumentMatchers.<Specification<Alert>>any())).thenReturn(List.of(late, early));

        List<AlertService.DayCount> stats = service().stats(LocalDate.of(2020, 1, 10), LocalDate.of(2020, 1, 11),
                ALL);

        assertThat(stats).containsExactly(
                new AlertService.DayCount(RULE, LocalDate.of(2020, 1, 10), 1),
                new AlertService.DayCount(RULE, LocalDate.of(2020, 1, 11), 1));
    }

    @Test
    void escalationSkipsAlertsOfInactiveRules() {
        when(rules.findAll()).thenReturn(List.of(rule(false)));
        when(alerts.findByStatus(Alert.Status.OPEN)).thenReturn(List.of(alert(Alert.Severity.WARN, null, NOW)));

        assertThat(service().escalateDue(NOW.plus(60, ChronoUnit.MINUTES))).isZero();
    }

    private AlertService service() {
        return new AlertService(rules, alerts, mock(HistoryRepository.class), mock(RecipientRepository.class),
                mock(IdentityDirectory.class), mock(NotificationService.class), mock(RealtimePublisher.class),
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC), mock(MasterdataQueries.class),
                mock(EmailService.class), mock(ExportService.class), mock(AlertNotifyMetrics.class));
    }

    private static AlertRule rule(boolean active) {
        AlertRule rule = new AlertRule(RULE, "MT", RULE, Alert.Severity.WARN, POLICY, ESCALATION, 30);
        rule.configure(null, null, null, null, null, active);
        return rule;
    }

    private static Alert alert(Alert.Severity severity, String lineCode, Instant raisedAt) {
        return new Alert(RULE, severity, "MT", "Machine", "M-" + UUID.randomUUID(), null, lineCode, null, "Tiêu đề",
                "Chi tiết", "{}", RULE + ":Machine:" + UUID.randomUUID(), raisedAt, 1, "{}");
    }

    private static AlertCommand command(String subject) {
        return new AlertCommand(RULE, "Machine", subject, null, null, "Cảnh báo", "Chi tiết", null, null, null);
    }
}
