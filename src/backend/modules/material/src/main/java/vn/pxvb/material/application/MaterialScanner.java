package vn.pxvb.material.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.pxvb.alerting.contract.AlertRaiser;
import vn.pxvb.alerting.contract.AlertRaiser.AlertCommand;
import vn.pxvb.production.contract.ProductionQueries;

/**
 * Mỗi phút: tính tiêu hao thời gian thực cho ca đang mở (cảnh báo {@code MT_WASTE_HIGH} ≤ 5') và phát
 * {@code MT_SLOT_LATE} cho khung giờ đã qua mà chưa giao nhận xong (BR-MT-005).
 */
@Component
public class MaterialScanner {

    private final ProductionQueries production;
    private final ConsumptionService consumption;
    private final AlertRaiser alerts;
    private final Clock clock;
    private final boolean enabled;

    public MaterialScanner(ProductionQueries production, ConsumptionService consumption, AlertRaiser alerts,
                           Clock clock, @Value("${pxvb.material.scanner.enabled:false}") boolean enabled) {
        this.production = production;
        this.consumption = consumption;
        this.alerts = alerts;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${pxvb.material.scanner.interval-ms:60000}")
    public void scheduled() {
        if (enabled) {
            scan(clock.instant());
        }
    }

    /** @return số cảnh báo khung giờ trễ đã phát. */
    public int scan(Instant now) {
        int late = 0;
        for (ProductionQueries.SessionRef s : production.openSessions()) {
            consumption.compute(s.id());
            for (Map<String, Object> l : consumption.lateLines(s.businessDate(), s.shiftCode(), now)) {
                alerts.raise(new AlertCommand("MT_SLOT_LATE", "IssueSlipLine", (String) l.get("lineId"),
                        java.util.UUID.fromString((String) l.get("machineId")), s.id(),
                        "Khung giờ " + l.get("slotNo") + " trễ giao nhận NVL", "Khung giờ " + l.get("slotNo")
                        + " đã qua: " + l.get("reason"), Map.of("slotNo", l.get("slotNo")), null, null));
                late++;
            }
        }
        return late;
    }
}
