package vn.pxvb.alerting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;

/** Vòng đời, tái diễn, nâng mức và leo thang của {@link Alert} (E04 5.1). */
class AlertTest {

    private static final Instant NOW = Instant.parse("2026-10-25T03:00:00Z");

    @Test
    void newAlertStartsOpenWithOneOccurrenceAndNoEscalation() {
        Alert alert = newAlert(Alert.Severity.WARN);

        assertThat(alert.getStatus()).isEqualTo(Alert.Status.OPEN);
        assertThat(alert.getOccurrenceCount()).isEqualTo(1);
        assertThat(alert.getEscalationLevel()).isZero();
    }

    @Test
    void acknowledgeRecordsTimeAndUserThenStartWorkFollowsLifecycle() {
        Alert alert = newAlert(Alert.Severity.WARN);
        UUID user = UUID.randomUUID();

        alert.transitionTo(Alert.Status.ACK, user, NOW);
        assertThat(alert.getStatus()).isEqualTo(Alert.Status.ACK);
        assertThat(alert.getAckAt()).isEqualTo(NOW);
        assertThat(alert.getAckBy()).isEqualTo(user);

        alert.transitionTo(Alert.Status.IN_PROGRESS, user, NOW);
        assertThat(alert.getStatus()).isEqualTo(Alert.Status.IN_PROGRESS);
    }

    @Test
    void resolvingRecordsCauseNoteAndTime() {
        Alert alert = newAlert(Alert.Severity.WARN);
        UUID user = UUID.randomUUID();
        alert.transitionTo(Alert.Status.ACK, user, NOW);

        alert.transitionTo(Alert.Status.RESOLVED, user, NOW.plus(10, ChronoUnit.MINUTES));
        alert.resolutionDetails("MAY_LOI", "Đã thay dao");

        assertThat(alert.getStatus()).isEqualTo(Alert.Status.RESOLVED);
        assertThat(alert.getResolvedAt()).isEqualTo(NOW.plus(10, ChronoUnit.MINUTES));
        assertThat(alert.getCause()).isEqualTo("MAY_LOI");
        assertThat(alert.getResolutionNote()).isEqualTo("Đã thay dao");
    }

    @Test
    void openAlertCannotBeResolvedWithoutAcknowledgement() {
        Alert alert = newAlert(Alert.Severity.WARN);

        assertThatThrownBy(() -> alert.transitionTo(Alert.Status.RESOLVED, UUID.randomUUID(), NOW))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_STATE_TRANSITION);
        assertThat(alert.getStatus()).isEqualTo(Alert.Status.OPEN);
    }

    @Test
    void recurrenceCountsOccurrencesAndKeepsNewestTimeAndMessage() {
        Alert alert = newAlert(Alert.Severity.WARN);
        Instant later = NOW.plus(5, ChronoUnit.MINUTES);

        alert.recordRecurrence(later, "Tái diễn", "{\"v\":2}");

        assertThat(alert.getOccurrenceCount()).isEqualTo(2);
        assertThat(alert.getLastOccurredAt()).isEqualTo(later);
        assertThat(alert.getMessage()).isEqualTo("Tái diễn");
    }

    @Test
    void recurrenceWithoutNewContentKeepsOriginalMessage() {
        Alert alert = newAlert(Alert.Severity.WARN);

        alert.recordRecurrence(NOW.plus(1, ChronoUnit.MINUTES), null, null);

        assertThat(alert.getOccurrenceCount()).isEqualTo(2);
        assertThat(alert.getMessage()).isEqualTo("Chi tiết");
    }

    @Test
    void severityOnlyRisesAndSameOrLowerReportsNoChange() {
        Alert alert = newAlert(Alert.Severity.WARN);

        assertThat(alert.raiseSeverity(Alert.Severity.CRITICAL)).isTrue();
        assertThat(alert.getSeverity()).isEqualTo(Alert.Severity.CRITICAL);

        assertThat(alert.raiseSeverity(Alert.Severity.WARN)).isFalse();
        assertThat(alert.raiseSeverity(Alert.Severity.CRITICAL)).isFalse();
        assertThat(alert.getSeverity()).isEqualTo(Alert.Severity.CRITICAL);
    }

    @Test
    void escalationIncrementsLevelEachTime() {
        Alert alert = newAlert(Alert.Severity.WARN);

        alert.escalate();
        alert.escalate();

        assertThat(alert.getEscalationLevel()).isEqualTo(2);
    }

    private static Alert newAlert(Alert.Severity severity) {
        return new Alert("MT_WASTE_HIGH", severity, "MT", "Machine", "M-1", null, null, null, "Tiêu đề", "Chi tiết",
                "{}", "MT_WASTE_HIGH:Machine:M-1", NOW, 1, "{}");
    }
}
