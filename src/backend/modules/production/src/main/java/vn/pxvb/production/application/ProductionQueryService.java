package vn.pxvb.production.application;

import static vn.pxvb.production.application.ProductionTime.instant;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.masterdata.contract.MasterdataQueries;
import vn.pxvb.masterdata.contract.MasterdataQueries.MachineInfo;
import vn.pxvb.masterdata.contract.MasterdataQueries.ProductInfo;

/** Truy vấn theo dõi sản lượng thời gian thực (FR-PR-006, FR-PR-010): lũy kế thực tế vs kế hoạch-đến-thời-điểm. */
@Service
public class ProductionQueryService {

    private final JdbcTemplate jdbc;
    private final MasterdataQueries masterdata;
    private final Clock clock;

    public ProductionQueryService(JdbcTemplate jdbc, MasterdataQueries masterdata, Clock clock) {
        this.jdbc = jdbc;
        this.masterdata = masterdata;
        this.clock = clock;
    }

    /** Kế hoạch-đến-thời-điểm: phân bổ tuyến tính theo thời gian đã trôi qua của ca. */
    static long plannedToDate(Long plannedPacks, Instant start, Instant end, Instant now) {
        if (plannedPacks == null || plannedPacks <= 0 || !end.isAfter(start)) {
            return 0;
        }
        double ratio = (double) Duration.between(start, now).toSeconds()
                / Duration.between(start, end).toSeconds();
        return Math.round(plannedPacks * Math.max(0, Math.min(1, ratio)));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(UUID sessionId) {
        List<Map<String, Object>> sessions = jdbc.queryForList("SELECT business_date, shift_code, status, "
                + "planned_start, planned_end, actual_start FROM pr_shift_session WHERE id = UUID_TO_BIN(?)",
                sessionId.toString());
        if (sessions.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên ca");
        }
        Map<String, Object> s = sessions.get(0);
        Instant now = clock.instant();
        Instant plannedStart = instant(s.get("planned_start"));
        Instant plannedEnd = instant(s.get("planned_end"));
        Instant from = s.get("actual_start") == null ? plannedStart : instant(s.get("actual_start"));
        Instant clockAt = "OPEN".equals(s.get("status")) ? now : plannedEnd;
        int interval = ShiftSessionService.DEFAULT_OUTPUT_INTERVAL_MIN;

        List<Map<String, Object>> runs = jdbc.queryForList("SELECT BIN_TO_UUID(r.machine_id) AS machineId, "
                + "r.status, r.order_code AS orderCode, BIN_TO_UUID(r.current_product_id) AS productId, "
                + "r.segment_no AS segmentNo, r.planned_packs AS plannedPacks, r.output_qty AS outputQty, "
                + "r.output_count AS outputCount, r.run_minutes AS runMinutes, r.planned_stop_minutes AS "
                + "plannedStopMinutes, r.unplanned_stop_minutes AS unplannedStopMinutes, "
                + "(SELECT MAX(e.period_to) FROM pr_output_entry e WHERE e.run_id = r.id) AS lastOutputAt, "
                + "(SELECT s2.reason_code FROM pr_stop_event s2 WHERE s2.run_id = r.id AND s2.end_at IS NULL "
                + "LIMIT 1) AS openStopReason, (SELECT s2.start_at FROM pr_stop_event s2 WHERE s2.run_id = r.id "
                + "AND s2.end_at IS NULL LIMIT 1) AS openStopSince FROM pr_machine_run r WHERE r.session_id = "
                + "UUID_TO_BIN(?)", sessionId.toString());
        List<UUID> machineIds = runs.stream().map(r -> UUID.fromString((String) r.get("machineId"))).toList();
        Map<UUID, MachineInfo> machines = masterdata.machines(machineIds);
        List<UUID> productIds = runs.stream().filter(r -> r.get("productId") != null)
                .map(r -> UUID.fromString((String) r.get("productId"))).distinct().toList();
        Map<UUID, ProductInfo> products = masterdata.products(productIds);

        List<Map<String, Object>> items = new ArrayList<>();
        long plannedTotal = 0;
        long actualTotal = 0;
        for (Map<String, Object> r : runs) {
            MachineInfo machine = machines.get(UUID.fromString((String) r.get("machineId")));
            boolean packer = machine != null && "PACKER".equals(machine.machineType());
            Long plannedPacks = r.get("plannedPacks") == null ? null : ((Number) r.get("plannedPacks")).longValue();
            long actual = ((Number) r.get("outputQty")).longValue();
            long planToDate = packer ? plannedToDate(plannedPacks, from, plannedEnd, clockAt) : 0;
            Instant lastOutput = instant(r.get("lastOutputAt"));
            Instant reference = lastOutput == null ? from : lastOutput;
            boolean overdue = "OPEN".equals(s.get("status")) && !"NO_ORDER".equals(r.get("status"))
                    && Duration.between(reference, now).toMinutes() > interval;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("machineId", r.get("machineId"));
            row.put("machineCode", machine == null ? null : machine.code());
            row.put("machineType", machine == null ? null : machine.machineType());
            row.put("status", r.get("status"));
            row.put("orderCode", r.get("orderCode"));
            row.put("productId", r.get("productId"));
            ProductInfo product = r.get("productId") == null ? null
                    : products.get(UUID.fromString((String) r.get("productId")));
            row.put("productCode", product == null ? null : product.code());
            row.put("segmentNo", r.get("segmentNo"));
            row.put("plannedPacks", packer ? plannedPacks : null);
            row.put("plannedToDate", planToDate);
            row.put("outputQty", actual);
            row.put("achievementPct", planToDate > 0 ? Math.round(actual * 1000.0 / planToDate) / 10.0 : null);
            row.put("outputCount", r.get("outputCount"));
            row.put("lastOutputAt", lastOutput);
            row.put("outputOverdue", overdue);
            row.put("openStopReason", r.get("openStopReason"));
            row.put("openStopSince", instant(r.get("openStopSince")));
            row.put("runMinutes", r.get("runMinutes"));
            row.put("plannedStopMinutes", r.get("plannedStopMinutes"));
            row.put("unplannedStopMinutes", r.get("unplannedStopMinutes"));
            items.add(row);
            if (packer) {
                plannedTotal += planToDate;
                actualTotal += actual;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", sessionId.toString());
        out.put("businessDate", s.get("business_date"));
        out.put("shiftCode", s.get("shift_code"));
        out.put("status", s.get("status"));
        out.put("asOf", clockAt);
        out.put("plannedToDatePacks", plannedTotal);
        out.put("actualPacks", actualTotal);
        out.put("machines", items);
        return out;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> outputs(UUID sessionId, UUID machineId) {
        return jdbc.queryForList("SELECT BIN_TO_UUID(e.id) AS id, BIN_TO_UUID(e.machine_id) AS machineId, "
                + "BIN_TO_UUID(e.product_id) AS productId, e.segment_no AS segmentNo, e.period_from AS "
                + "periodFrom, e.period_to AS periodTo, e.qty, e.unit, e.counter_reading AS counterReading, "
                + "e.note, BIN_TO_UUID(e.entered_by) AS enteredBy, BIN_TO_UUID(e.on_behalf_of) AS onBehalfOf "
                + "FROM pr_output_entry e JOIN pr_machine_run r ON r.id = e.run_id WHERE r.session_id = "
                + "UUID_TO_BIN(?)" + (machineId == null ? "" : " AND e.machine_id = UUID_TO_BIN(?)")
                + " ORDER BY e.period_from", machineId == null ? new Object[]{sessionId.toString()}
                : new Object[]{sessionId.toString(), machineId.toString()});
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> stops(UUID sessionId, UUID machineId) {
        return jdbc.queryForList(RecordingService.stopSql() + " JOIN pr_machine_run r ON r.id = s.run_id "
                + "WHERE r.session_id = UUID_TO_BIN(?)" + (machineId == null ? ""
                : " AND s.machine_id = UUID_TO_BIN(?)") + " ORDER BY s.start_at",
                machineId == null ? new Object[]{sessionId.toString()}
                        : new Object[]{sessionId.toString(), machineId.toString()});
    }
}
