package vn.pxvb.alerting.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.pxvb.alerting.application.AlertRuleService;
import vn.pxvb.alerting.application.AlertRuleService;
import vn.pxvb.alerting.application.Repositories.RuleRepository;
import vn.pxvb.alerting.domain.Alert;
import vn.pxvb.alerting.domain.AlertRule;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.common.security.CurrentUser;

/** Cấu hình rule cảnh báo (FR-CC-050). Mỗi thay đổi bắt buộc có lý do và được audit trước/sau (L67). */
@RestController
@RequestMapping("/api/v1/alerting/rules")
public class AlertRuleController {

    public record RuleDto(String code, String module, String metric, String severity, String condition,
                          String recipientPolicy, String escalationPolicy, int dedupWindowMin, boolean active,
                          int version) {
        static RuleDto from(AlertRule r) {
            return new RuleDto(r.getCode(), r.getModule(), r.getMetric(), r.getSeverity().name(),
                    r.getConditionJson(), r.getRecipientPolicy(), r.getEscalationPolicy(), r.getDedupWindowMin(),
                    r.isActive(), r.getVersion());
        }
    }

    public record RuleUpdate(Alert.Severity severity, String condition, String recipientPolicy,
                             String escalationPolicy, Integer dedupWindowMin, Boolean active, String reason) {
    }

    private final RuleRepository rules;
    private final ObjectMapper mapper;
    private final AlertRuleService ruleService;
    private final CurrentUser currentUser;

    public AlertRuleController(RuleRepository rules, ObjectMapper mapper, AlertRuleService ruleService,
                               CurrentUser currentUser) {
        this.rules = rules;
        this.mapper = mapper;
        this.ruleService = ruleService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('alerting.alert.view')")
    @Transactional(readOnly = true)
    public List<RuleDto> list() {
        return rules.findAll().stream().map(RuleDto::from).toList();
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('alerting.rule.config')")
    @Transactional
    public RuleDto update(@PathVariable String code, @RequestBody RuleUpdate req) {
        requireJson(req.condition());
        requireJson(req.recipientPolicy());
        requireJson(req.escalationPolicy());
        if (req.dedupWindowMin() != null && req.dedupWindowMin() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cửa sổ khử trùng không được âm");
        }
        var principal = currentUser.require();
        AlertRule rule = ruleService.update(code, new AlertRuleService.RuleChange(req.severity(), req.condition(),
                req.recipientPolicy(), req.escalationPolicy(), req.dedupWindowMin(), req.active(), req.reason()),
                principal.userId(), principal.deviceId());
        return RuleDto.from(rule);
    }

    private void requireJson(String value) {
        if (value == null) {
            return;
        }
        try {
            mapper.readTree(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cấu hình không phải JSON hợp lệ");
        }
    }
}
