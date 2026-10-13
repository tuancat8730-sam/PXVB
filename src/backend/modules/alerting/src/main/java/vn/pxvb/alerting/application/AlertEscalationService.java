package vn.pxvb.alerting.application;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Lịch quét leo thang mỗi phút (SDD §4.9 AlertScanner). Chỉ chạy khi {@code pxvb.alerting.scanner.enabled}. */
@Component
public class AlertEscalationService {

    private final AlertService alerts;
    private final Clock clock;
    private final boolean enabled;

    public AlertEscalationService(AlertService alerts, Clock clock,
                                  @Value("${pxvb.alerting.scanner.enabled:false}") boolean enabled) {
        this.alerts = alerts;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${pxvb.alerting.scanner.interval-ms:60000}")
    public void scheduled() {
        if (enabled) {
            alerts.escalateDue(clock.instant());
        }
    }
}
