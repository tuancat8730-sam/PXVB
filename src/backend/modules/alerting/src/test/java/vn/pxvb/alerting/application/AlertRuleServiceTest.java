package vn.pxvb.alerting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.pxvb.alerting.application.AlertRuleService.RuleChange;
import vn.pxvb.alerting.application.Repositories.RuleRepository;
import vn.pxvb.alerting.domain.Alert;
import vn.pxvb.alerting.domain.AlertRule;
import vn.pxvb.common.audit.AuditLogWriter;
import vn.pxvb.common.audit.AuditLogWriter.Entry;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;

/** Sửa rule: bắt buộc lý do, audit trước/sau, tăng phiên bản (E04 3.3, 5.1). */
class AlertRuleServiceTest {

    private final RuleRepository rules = mock(RuleRepository.class);
    private final AuditLogWriter audit = mock(AuditLogWriter.class);
    private final AlertRuleService service = new AlertRuleService(rules, audit);

    @Test
    void changeWithoutReasonIsRejectedAndNothingIsWritten() {
        assertThatThrownBy(() -> service.update("MT_WASTE_HIGH", change(45, "  "), UUID.randomUUID(), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION);
        verify(audit, never()).write(any());
    }

    @Test
    void unknownRuleIsNotFound() {
        when(rules.findByCode(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("NO_SUCH", change(45, "Lý do"), UUID.randomUUID(), null))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    @Test
    void changeIsAuditedWithOnlyTheChangedFieldsAndTrimmedReason() {
        AlertRule rule = new AlertRule("MT_WASTE_HIGH", "MT", "MT_WASTE_HIGH", Alert.Severity.WARN, "{}", "{}", 30);
        when(rules.findByCode("MT_WASTE_HIGH")).thenReturn(Optional.of(rule));
        UUID user = UUID.randomUUID();

        AlertRule updated = service.update("MT_WASTE_HIGH", change(45, "  Giảm trễ phát hiện  "), user, null);

        ArgumentCaptor<Entry> written = ArgumentCaptor.forClass(Entry.class);
        verify(audit).write(written.capture());
        Entry entry = written.getValue();
        assertThat(entry.entityType()).isEqualTo("AlertRule");
        assertThat(entry.entityId()).isEqualTo("MT_WASTE_HIGH");
        assertThat(entry.reason()).isEqualTo("Giảm trễ phát hiện");
        assertThat(entry.byUserId()).isEqualTo(user);
        assertThat(entry.changes()).hasSize(1);
        assertThat(entry.changes().get(0).field()).isEqualTo("dedupWindowMin");
        assertThat(entry.changes().get(0).oldValue()).isEqualTo(30);
        assertThat(entry.changes().get(0).newValue()).isEqualTo(45);
        assertThat(updated.getVersion()).isEqualTo(2);
    }

    private static RuleChange change(int dedupWindowMin, String reason) {
        return new RuleChange(null, null, null, null, dedupWindowMin, null, reason);
    }
}
