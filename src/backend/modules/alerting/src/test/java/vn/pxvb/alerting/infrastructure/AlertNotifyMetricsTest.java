package vn.pxvb.alerting.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import vn.pxvb.alerting.domain.Alert;

/** Độ trễ raise → push sau commit (E04 4.1) và ngưỡng NFR-004 cấu hình được (E04 4.2). */
class AlertNotifyMetricsTest {

    private static final String LATENCY = "pxvb.alert.notify.latency";
    private static final String BREACH = "pxvb.alert.notify.breach";

    @Test
    void recordsLatencyTimerTaggedBySeverity() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AlertNotifyMetrics metrics = new AlertNotifyMetrics(registry, Duration.ofMinutes(5));

        metrics.recordPushed(Alert.Severity.WARN, metrics.start());

        assertThat(registry.get(LATENCY).tag("severity", "WARN").timer().count()).isEqualTo(1);
    }

    @Test
    void countsBreachWhenLatencyExceedsTarget() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AlertNotifyMetrics metrics = new AlertNotifyMetrics(registry, Duration.ZERO);

        metrics.recordPushed(Alert.Severity.CRITICAL, metrics.start());

        assertThat(registry.get(BREACH).tag("severity", "CRITICAL").counter().count()).isEqualTo(1);
    }

    @Test
    void doesNotCountBreachWithinTarget() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AlertNotifyMetrics metrics = new AlertNotifyMetrics(registry, Duration.ofHours(1));

        metrics.recordPushed(Alert.Severity.INFO, metrics.start());

        assertThat(registry.find(BREACH).counter()).isNull();
    }
}
