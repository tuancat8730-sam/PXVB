package vn.pxvb.alerting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import vn.pxvb.alerting.domain.Alert;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;

class AlertInputRulesTest {

    @Test
    void severityNameIsParsedToEnum() {
        // Arrange
        String input = "WARN";

        // Act
        Alert.Severity severity = AlertService.parseSeverity(input);

        // Assert
        assertThat(severity).isEqualTo(Alert.Severity.WARN);
    }

    @Test
    void missingSeverityMeansUseRuleDefault() {
        // Arrange / Act
        Alert.Severity severity = AlertService.parseSeverity(null);

        // Assert
        assertThat(severity).isNull();
    }

    @Test
    void unknownSeverityIsRejectedAsValidationError() {
        // Arrange
        String input = "BOGUS";

        // Act / Assert
        assertThatThrownBy(() -> AlertService.parseSeverity(input))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.VALIDATION);
                    assertThat(e.fieldErrors()).containsKey("severity");
                });
    }

    @Test
    void exportAllowsExactlyTheRowLimit() {
        // Arrange
        long total = AlertService.MAX_EXPORT_ROWS;

        // Act / Assert
        assertThatCode(() -> AlertService.requireWithinExportLimit(total)).doesNotThrowAnyException();
    }

    @Test
    void blankResolutionNoteIsRejectedAsValidationError() {
        // Arrange
        String input = "   ";

        // Act / Assert
        assertThatThrownBy(() -> AlertService.requireResolutionNote(input))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.VALIDATION);
                    assertThat(e.fieldErrors()).containsKey("note");
                });
    }

    @Test
    void missingResolutionNoteIsRejectedAsValidationError() {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> AlertService.requireResolutionNote(null))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.VALIDATION);
                    assertThat(e.fieldErrors()).containsKey("note");
                });
    }

    @Test
    void resolutionNoteIsTrimmedWhenPresent() {
        // Arrange
        String input = "  Đã bổ sung  ";

        // Act
        String note = AlertService.requireResolutionNote(input);

        // Assert
        assertThat(note).isEqualTo("Đã bổ sung");
    }

    @Test
    void exportOverRowLimitFailsInsteadOfTruncating() {
        // Arrange
        long total = AlertService.MAX_EXPORT_ROWS + 1;

        // Act / Assert
        assertThatThrownBy(() -> AlertService.requireWithinExportLimit(total))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.VALIDATION);
                    assertThat(e.getMessage()).contains("50.000");
                });
    }
}
