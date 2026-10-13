package vn.pxvb.workflow.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.outbox.OutboxPublisher;
import vn.pxvb.workflow.application.Repositories.InstanceRepository;
import vn.pxvb.workflow.application.Repositories.StepRepository;
import vn.pxvb.workflow.domain.ApprovalStep;

/**
 * Quét bước duyệt quá SLA (FR-CC-030 {@code onTimeout}): phát sự kiện {@code approval.overdue} đúng một lần
 * cho mỗi bước; consumer (alerting) thông báo/leo thang. Chỉ chạy lịch khi {@code pxvb.workflow.scanner.enabled}.
 */
@Component
public class ApprovalSlaScanner {

    private final StepRepository steps;
    private final InstanceRepository instances;
    private final OutboxPublisher outbox;
    private final Clock clock;
    private final boolean enabled;

    public ApprovalSlaScanner(StepRepository steps, InstanceRepository instances, OutboxPublisher outbox,
                              Clock clock, @Value("${pxvb.workflow.scanner.enabled:false}") boolean enabled) {
        this.steps = steps;
        this.instances = instances;
        this.outbox = outbox;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${pxvb.workflow.scanner.interval-ms:60000}")
    public void scheduled() {
        if (enabled) {
            scan(clock.instant());
        }
    }

    /** @return số bước vừa được đánh dấu quá hạn. */
    @Transactional
    public int scan(Instant now) {
        List<ApprovalStep> overdue = steps.findByStatusAndDueAtBeforeAndOverdueNotifiedAtIsNull(
                ApprovalStep.Status.PENDING, now);
        for (ApprovalStep step : overdue) {
            step.markOverdueNotified(now);
            instances.findById(step.getInstanceId()).ifPresent(instance -> outbox.publish("approval.overdue", Map.of(
                    "instanceId", instance.getId().toString(),
                    "flowCode", instance.getFlowCode(),
                    "stepNo", step.getStepNo(),
                    "roleCode", step.getRoleCode() == null ? "" : step.getRoleCode(),
                    "onTimeout", step.getOnTimeout().name(),
                    "startedBy", instance.getStartedBy().toString())));
        }
        return overdue.size();
    }
}
