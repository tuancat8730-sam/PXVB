package vn.pxvb.alerting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.pxvb.alerting.application.Repositories.RuleRepository;
import vn.pxvb.alerting.domain.AlertRule;

/** Danh mục rule seed (E04 5.1): không trùng mã, chính sách là JSON hợp lệ, không ghi đè rule đã có. */
class AlertRuleCatalogTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void seedsEveryRuleOnceWithUniqueCodesAndValidPolicies() throws Exception {
        RuleRepository rules = mock(RuleRepository.class);
        when(rules.findByCode(anyString())).thenReturn(Optional.empty());

        new AlertRuleCatalog(rules, mapper, transactions()).run(null);

        ArgumentCaptor<AlertRule> saved = ArgumentCaptor.forClass(AlertRule.class);
        verify(rules, atLeastOnce()).save(saved.capture());
        List<AlertRule> seeded = saved.getAllValues();
        assertThat(seeded.stream().map(AlertRule::getCode).toList()).doesNotHaveDuplicates();
        for (AlertRule rule : seeded) {
            assertThat(parse(rule.getRecipientPolicy()).has("roles")).as(rule.getCode()).isTrue();
            assertThat(parse(rule.getEscalationPolicy()).has("levels")).as(rule.getCode()).isTrue();
        }
    }

    @Test
    void doesNotOverwriteRulesThatAlreadyExist() {
        RuleRepository rules = mock(RuleRepository.class);
        when(rules.findByCode(anyString())).thenReturn(Optional.of(mock(AlertRule.class)));

        new AlertRuleCatalog(rules, mapper, transactions()).run(null);

        verify(rules, never()).save(any());
    }

    private JsonNode parse(String json) throws Exception {
        return mapper.readTree(json);
    }

    private static TransactionTemplate transactions() {
        return new TransactionTemplate(mock(PlatformTransactionManager.class));
    }
}
