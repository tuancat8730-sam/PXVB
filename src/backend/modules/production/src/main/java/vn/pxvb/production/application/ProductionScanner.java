package vn.pxvb.production.application;

import static vn.pxvb.production.application.ProductionTime.instant;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.pxvb.alerting.contract.AlertRaiser;
import vn.pxvb.alerting.contract.AlertRaiser.AlertCommand;
import vn.pxvb.masterdata.contract.MasterdataQueries;
import vn.pxvb.masterdata.contract.MasterdataQueries.MachineInfo;

/**
 * Quét ca đang mở để phát {@code PR_OUTPUT_BEHIND} (AC-PR-005: máy bao thấp hơn kế hoạch-đến-thời-điểm quá ngưỡng
 * sau ≥ 60' từ giờ vào ca) và {@code PR_STOP_LONG} (dừng đang mở quá ngưỡng theo phút; ≥ 2 × ngưỡng → CRITICAL).
 */
@Component
public class ProductionScanner {

    private static final int MIN_ELAPSED_MINUTES = 60;
    private static final BigDecimal DEFAULT_BEHIND_PCT = BigDecimal.valueOf(15);
    private static final BigDecimal DEFAULT_STOP_LONG_MIN = BigDecimal.valueOf(30);

    private final JdbcTemplate jdbc;
    private final MasterdataQueries masterdata;
    private final AlertRaiser alerts;
    private final Clock clock;
    private final boolean enabled;

    public ProductionScanner(JdbcTemplate jdbc, MasterdataQueries masterdata, AlertRaiser alerts, Clock clock,
                             @Value("${pxvb.production.scanner.enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.masterdata = masterdata;
        this.alerts = alerts;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${pxvb.production.scanner.interval-ms:60000}")
    public void scheduled() {
        if (enabled) {
            scan(clock.instant());
        }
    }

    /** @return số cảnh báo đã phát/cập nhật. */
    public int scan(Instant now) {
        int raised = 0;
        List<Map<String, Object>> sessions = jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, business_date, "
                + "actual_start, planned_end FROM pr_shift_session WHERE status = 'OPEN'");
        for (Map<String, Object> s : sessions) {
            UUID sessionId = UUID.fromString((String) s.get("id"));
            LocalDate date = ((java.sql.Date) s.get("business_date")).toLocalDate();
            Instant start = instant(s.get("actual_start"));
            Instant end = instant(s.get("planned_end"));
            raised += scanBehind(sessionId, date, start, end, now);
            raised += scanLongStops(sessionId, date, now);
        }
        return raised;
    }

    private int scanBehind(UUID sessionId, LocalDate date, Instant start, Instant end, Instant now) {
        if (Duration.between(start, now).toMinutes() < MIN_ELAPSED_MINUTES) {
            return 0;
        }
        int raised = 0;
        List<Map<String, Object>> runs = jdbc.queryForList("SELECT BIN_TO_UUID(r.machine_id) AS machineId, "
                + "r.planned_packs, r.output_qty, r.order_code FROM pr_machine_run r WHERE r.session_id = "
                + "UUID_TO_BIN(?) AND r.order_id IS NOT NULL AND r.planned_packs > 0", sessionId.toString());
        Map<UUID, MachineInfo> machines = masterdata.machines(runs.stream()
                .map(r -> UUID.fromString((String) r.get("machineId"))).toList());
        for (Map<String, Object> r : runs) {
            UUID machineId = UUID.fromString((String) r.get("machineId"));
            MachineInfo machine = machines.get(machineId);
            if (machine == null || !"PACKER".equals(machine.machineType())) {
                continue; // kế hoạch tính theo bao: chỉ máy bao
            }
            long planned = ((Number) r.get("planned_packs")).longValue();
            long actual = ((Number) r.get("output_qty")).longValue();
            long toDate = ProductionQueryService.plannedToDate(planned, start, end, now);
            BigDecimal pct = threshold("PR_OUTPUT_BEHIND_PCT", machineId, date, DEFAULT_BEHIND_PCT);
            double limit = toDate * (1 - pct.doubleValue() / 100.0);
            if (toDate > 0 && actual < limit) {
                alerts.raise(new AlertCommand("PR_OUTPUT_BEHIND", "Machine", machineId.toString(), machineId,
                        sessionId, "Máy " + machine.code() + " chậm sản lượng",
                        "Thực tế " + actual + " bao / kế hoạch đến thời điểm " + toDate + " bao (lệch quá "
                                + pct.stripTrailingZeros().toPlainString() + "%)",
                        Map.of("actual", actual, "plannedToDate", toDate, "orderCode",
                                String.valueOf(r.get("order_code"))), null, null));
                raised++;
            }
        }
        return raised;
    }

    private int scanLongStops(UUID sessionId, LocalDate date, Instant now) {
        int raised = 0;
        List<Map<String, Object>> stops = jdbc.queryForList("SELECT BIN_TO_UUID(s.id) AS id, "
                + "BIN_TO_UUID(s.machine_id) AS machineId, s.reason_code, s.start_at FROM pr_stop_event s "
                + "JOIN pr_machine_run r ON r.id = s.run_id WHERE r.session_id = UUID_TO_BIN(?) "
                + "AND s.end_at IS NULL AND s.planned_downtime = 0", sessionId.toString());
        for (Map<String, Object> s : stops) {
            UUID machineId = UUID.fromString((String) s.get("machineId"));
            long minutes = Duration.between(instant(s.get("start_at")), now).toMinutes();
            BigDecimal limit = threshold("PR_STOP_LONG_MIN", machineId, date, DEFAULT_STOP_LONG_MIN);
            if (minutes > limit.longValue()) {
                String severity = minutes >= 2 * limit.longValue() ? "CRITICAL" : null;
                alerts.raise(new AlertCommand("PR_STOP_LONG", "StopEvent", (String) s.get("id"), machineId,
                        sessionId, "Máy dừng kéo dài " + minutes + " phút",
                        "Mã dừng " + s.get("reason_code") + ", đã dừng " + minutes + " phút (ngưỡng "
                                + limit.stripTrailingZeros().toPlainString() + " phút)",
                        Map.of("minutes", minutes, "reasonCode", String.valueOf(s.get("reason_code"))), null,
                        severity));
                raised++;
            }
        }
        return raised;
    }

    private BigDecimal threshold(String metric, UUID machineId, LocalDate date, BigDecimal fallback) {
        return masterdata.resolveThreshold(metric, machineId, null, null, null, null, date)
                .map(t -> t.value()).orElse(fallback);
    }
}
