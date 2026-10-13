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
import vn.pxvb.alerting.application.Repositories.RuleRepository;
import vn.pxvb.alerting.domain.Alert;
import vn.pxvb.alerting.domain.AlertRule;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;

/** Cấu hình rule cảnh báo (FR-CC-050). Mọi thay đổi được audit tự động qua {@code AlertRule}. */
@RestController
@RequestMapping("/api/v1/alerting/rules")
public class AlertRuleController {

    public record RuleDto(String code, String module, String metric, String severity, String condition,
                          String recipientPolicy, String escalationPolicy, int dedupWindowMin, boolean active) {
        static RuleDto from(AlertRule r) {
            return new RuleDto(r.getCode(), r.getModule(), r.getMetric(), r.getSeverity().name(),
                    r.getConditionJson(), r.getRecipientPolicy(), r.getEscalationPolicy(), r.getDedupWindowMin(),
                    r.isActive());
        }
    }

    public record RuleUpdate(Alert.Severity severity, String condition, String recipientPolicy,
                             String escalationPolicy, Integer dedupWindowMin, Boolean active) {
    }

    private final RuleRepository rules;
    private final ObjectMapper mapper;

    public AlertRuleController(RuleRepository rules, ObjectMapper mapper) {
        this.rules = rules;
        this.mapper = mapper;
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
        AlertRule rule = rules.findByCode(code).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        requireJson(req.condition());
        requireJson(req.recipientPolicy());
        requireJson(req.escalationPolicy());
        if (req.dedupWindowMin() != null && req.dedupWindowMin() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cửa sổ khử trùng không được âm");
        }
        rule.configure(req.severity(), req.condition(), req.recipientPolicy(), req.escalationPolicy(),
                req.dedupWindowMin(), req.active());
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
