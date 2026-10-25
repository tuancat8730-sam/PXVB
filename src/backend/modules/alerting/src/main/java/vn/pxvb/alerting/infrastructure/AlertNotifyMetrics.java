package vn.pxvb.alerting.infrastructure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.pxvb.alerting.domain.Alert;

/**
 * Đo độ trễ raise → push sau commit (E04 4.1) và đếm lần vượt ngưỡng NFR-004 (E04 4.2).
 * Ngưỡng đọc từ {@code pxvb.alerting.notify-latency-target}, mặc định 5 phút.
 */
@Component
public class AlertNotifyMetrics {

    static final String LATENCY = "pxvb.alert.notify.latency";
    static final String BREACH = "pxvb.alert.notify.breach";

    private final MeterRegistry registry;
    private final Duration target;

    public AlertNotifyMetrics(MeterRegistry registry,
                              @Value("${pxvb.alerting.notify-latency-target:PT5M}") Duration target) {
        this.registry = registry;
        this.target = target;
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    public void recordPushed(Alert.Severity severity, Timer.Sample sample) {
        String tag = severity.name();
        Timer timer = Timer.builder(LATENCY).tag("severity", tag).register(registry);
        long elapsedNanos = sample.stop(timer);
        if (Duration.ofNanos(elapsedNanos).compareTo(target) > 0) {
            registry.counter(BREACH, "severity", tag).increment();
        }
    }
}
