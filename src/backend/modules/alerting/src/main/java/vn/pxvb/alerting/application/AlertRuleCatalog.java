package vn.pxvb.alerting.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import vn.pxvb.alerting.application.Repositories.RuleRepository;
import vn.pxvb.alerting.domain.Alert.Severity;
import vn.pxvb.alerting.domain.AlertRule;

/**
 * Danh mục rule cảnh báo chuẩn (SDD §4.6). Người nhận/leo thang là giả định ⚠ (Q19): chỉ seed khi chưa có,
 * điều chỉnh qua API cấu hình rule. Ngưỡng nằm ở masterdata {@code md.Threshold}, không phải ở đây.
 */
@Component
public class AlertRuleCatalog implements ApplicationRunner {

    private record Def(String code, String module, Severity severity, List<String> roles, Integer escalateAfterMin,
                        List<String> escalateRoles) {
    }

    private static Def def(String code, String module, Severity severity, List<String> roles) {
        return new Def(code, module, severity, roles, null, List.of());
    }

    private static Def esc(String code, String module, Severity severity, List<String> roles, int afterMin,
                           List<String> escalateRoles) {
        return new Def(code, module, severity, roles, afterMin, escalateRoles);
    }

    private static final List<Def> CATALOG = List.of(
            esc("PR_OUTPUT_BEHIND", "PR", Severity.WARN, List.of("SHIFT_LEADER"), 30, List.of("DEPUTY_DIRECTOR")),
            def("PR_OEE_LOW", "PR", Severity.WARN, List.of("SHIFT_LEADER", "DEPUTY_DIRECTOR")),
            esc("PR_STOP_LONG", "PR", Severity.WARN, List.of("SHIFT_LEADER"), 15, List.of("DEPUTY_DIRECTOR")),
            def("PR_BUFFER_HIGH", "PR", Severity.WARN, List.of("SHIFT_LEADER")),
            def("PR_BUFFER_LOW", "PR", Severity.WARN, List.of("SHIFT_LEADER")),
            def("MT_ISSUE_OVER_SLIP", "MT", Severity.WARN, List.of("STAT_MATERIAL", "SHIFT_LEADER")),
            def("MT_WASTE_HIGH", "MT", Severity.WARN, List.of("SHIFT_LEADER", "DEPUTY_DIRECTOR")),
            def("MT_SLOT_LATE", "MT", Severity.WARN, List.of("STAT_MATERIAL", "SHIFT_LEADER")),
            def("QC_DEFECT_C", "QC", Severity.INFO, List.of("OPERATOR")),
            def("QC_DEFECT_B", "QC", Severity.WARN, List.of("SHIFT_LEADER", "SUPERVISOR")),
            def("QC_DEFECT_A", "QC", Severity.CRITICAL, List.of("DEPUTY_DIRECTOR", "QA_HEAD", "QA_SUPERVISOR")),
            esc("QC_CHECK_LATE", "QC", Severity.WARN, List.of("OPERATOR"), 10, List.of("SUPERVISOR")),
            def("QC_CHECK_MISSED", "QC", Severity.WARN, List.of("SUPERVISOR", "SHIFT_LEADER")),
            def("QC_PHYSICAL_OOS", "QC", Severity.WARN, List.of("QA_SUPERVISOR", "QA_HEAD", "SHIFT_LEADER")),
            def("TX_WASTE_HIGH", "TX", Severity.WARN, List.of("SHIFT_LEADER", "STAT_STAMP")),
            def("TX_MISMATCH", "TX", Severity.CRITICAL, List.of("SHIFT_LEADER", "STAT_STAMP", "DEPUTY_DIRECTOR")),
            def("TX_BLOCK_LOW", "TX", Severity.INFO, List.of("STAT_STAMP")),
            def("PM_DUE_SOON", "PM", Severity.INFO, List.of("MNT_LEAD", "MNT_TECH")),
            def("PM_OVERDUE", "PM", Severity.WARN, List.of("MNT_LEAD", "MNT_TECH")),
            def("PM_WEEK_AT_RISK", "PM", Severity.WARN, List.of("MNT_LEAD")),
            esc("PM_REPAIR_PENDING", "PM", Severity.WARN, List.of("MNT_LEAD"), 15, List.of("DEPUTY_DIRECTOR")),
            def("PM_MTTR_MTBF_BREACH", "PM", Severity.WARN, List.of("MNT_TECH")),
            def("OH_DELAYED", "OH", Severity.WARN, List.of("KTCD")),
            def("OH_YEAR_AT_RISK", "OH", Severity.WARN, List.of("KTCD", "BOARD")),
            def("OH_PART_REPEAT", "OH", Severity.INFO, List.of("KTCD", "MNT_TECH")),
            def("OH_EXTRA_PART_PENDING", "OH", Severity.WARN, List.of("KTCD")),
            def("SP_STOCK_BELOW_MIN", "SP", Severity.WARN, List.of("WAREHOUSE_KEEPER", "KTCD")),
            def("CC_DEVICE_UNSYNCED", "CC", Severity.WARN, List.of("SHIFT_LEADER", "SYS_ADMIN")),
            def("CC_SYNC_CONFLICT", "CC", Severity.WARN, List.of("SHIFT_LEADER", "SYS_ADMIN")),
            esc("CC_SHIFT_NOT_CLOSED", "CC", Severity.WARN, List.of("SHIFT_LEADER"), 15,
                    List.of("DEPUTY_DIRECTOR")));

    private final RuleRepository rules;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public AlertRuleCatalog(RuleRepository rules, ObjectMapper mapper, TransactionTemplate tx) {
        this.rules = rules;
        this.mapper = mapper;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        tx.executeWithoutResult(status -> {
            for (Def d : CATALOG) {
                if (rules.findByCode(d.code()).isPresent()) {
                    continue;
                }
                int dedup = d.severity() == Severity.CRITICAL ? 10 : 30;
                rules.save(new AlertRule(d.code(), d.module(), d.code(), d.severity(),
                        json(Map.of("roles", d.roles())), escalationJson(d), dedup));
            }
        });
    }

    private String escalationJson(Def d) {
        if (d.escalateAfterMin() == null) {
            return json(Map.of("levels", List.of()));
        }
        return json(Map.of("levels", List.of(Map.of("afterMinutes", d.escalateAfterMin(),
                "roles", d.escalateRoles()))));
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
