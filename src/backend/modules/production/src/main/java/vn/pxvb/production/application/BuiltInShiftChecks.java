package vn.pxvb.production.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.pxvb.alerting.contract.AlertQueries;
import vn.pxvb.identity.contract.DeviceRegistry;
import vn.pxvb.masterdata.contract.MasterdataQueries;
import vn.pxvb.masterdata.contract.MasterdataQueries.MachineInfo;
import vn.pxvb.production.contract.ShiftCloseCheck;
import vn.pxvb.production.contract.ShiftCloseCheck.Item;
import vn.pxvb.production.contract.ShiftCloseCheck.Level;
import vn.pxvb.sync.contract.SyncQueries;

/**
 * Các kiểm tra chốt ca PR tự có dữ liệu (SDD §4.10). {@code CHK_MATERIAL/QUALITY/STAMP} do MT/QC/TX đăng ký
 * qua {@link ShiftCloseCheck} khi các phân hệ đó có mặt.
 */
@Configuration
class BuiltInShiftChecks {

    private static final BigDecimal DEFAULT_VARIANCE_PCT = BigDecimal.valueOf(15);

    private static String label(Map<java.util.UUID, MachineInfo> machines, UUID id) {
        MachineInfo m = machines.get(id);
        return m == null ? id.toString() : m.code();
    }

    /** BR-PR-005: đủ số lần ghi sản lượng tối thiểu = floor(ShiftMin / OUTPUT_INTERVAL_MIN); AC-PR-006. */
    @Bean
    ShiftCloseCheck outputCompleteCheck(JdbcTemplate jdbc, MasterdataQueries masterdata) {
        return simple("CHK_OUTPUT_COMPLETE", (sessionId, date, shift) -> {
            int interval = masterdata.resolveThreshold("OUTPUT_INTERVAL_MIN", null, null, null, null, null, date)
                    .map(t -> t.value().intValue()).filter(v -> v > 0)
                    .orElse(ShiftSessionService.DEFAULT_OUTPUT_INTERVAL_MIN);
            List<Map<String, Object>> runs = jdbc.queryForList("SELECT BIN_TO_UUID(machine_id) AS machineId, "
                    + "output_count, planned_minutes FROM pr_machine_run WHERE session_id = UUID_TO_BIN(?) "
                    + "AND status <> 'NO_ORDER'", sessionId.toString());
            Map<UUID, MachineInfo> machines = masterdata.machines(runs.stream()
                    .map(r -> UUID.fromString((String) r.get("machineId"))).toList());
            List<Item> items = new ArrayList<>();
            for (Map<String, Object> r : runs) {
                int required = Math.max(1, ((Number) r.get("planned_minutes")).intValue() / interval);
                int actual = ((Number) r.get("output_count")).intValue();
                if (actual < required) {
                    UUID machineId = UUID.fromString((String) r.get("machineId"));
                    items.add(new Item("CHK_OUTPUT_COMPLETE", machineId.toString(), label(machines, machineId),
                            Level.BLOCK, "Máy " + label(machines, machineId) + " mới ghi " + actual + "/" + required
                            + " lần sản lượng tối thiểu", true));
                }
            }
            return items;
        });
    }

    /** Sản lượng thực tế (máy bao) lệch kế hoạch > ngưỡng → bắt buộc giải trình nguyên nhân thiếu/vượt. */
    @Bean
    ShiftCloseCheck outputPlanCheck(JdbcTemplate jdbc, MasterdataQueries masterdata) {
        return simple("CHK_OUTPUT_PLAN", (sessionId, date, shift) -> {
            List<Map<String, Object>> runs = jdbc.queryForList("SELECT BIN_TO_UUID(machine_id) AS machineId, "
                    + "planned_packs, output_qty FROM pr_machine_run WHERE session_id = UUID_TO_BIN(?) "
                    + "AND order_id IS NOT NULL AND planned_packs > 0", sessionId.toString());
            Map<UUID, MachineInfo> machines = masterdata.machines(runs.stream()
                    .map(r -> UUID.fromString((String) r.get("machineId"))).toList());
            List<Item> items = new ArrayList<>();
            for (Map<String, Object> r : runs) {
                UUID machineId = UUID.fromString((String) r.get("machineId"));
                MachineInfo machine = machines.get(machineId);
                if (machine == null || !"PACKER".equals(machine.machineType())) {
                    continue;
                }
                long planned = ((Number) r.get("planned_packs")).longValue();
                long actual = ((Number) r.get("output_qty")).longValue();
                BigDecimal limit = masterdata.resolveThreshold("PR_OUTPUT_BEHIND_PCT", machineId, null, null, null,
                        null, date).map(t -> t.value()).orElse(DEFAULT_VARIANCE_PCT);
                double deviation = Math.abs(actual - planned) * 100.0 / planned;
                if (deviation > limit.doubleValue()) {
                    items.add(new Item("CHK_OUTPUT_PLAN", machineId.toString(), machine.code(), Level.EXPLAIN,
                            "Máy " + machine.code() + ": thực tế " + actual + " bao / kế hoạch " + planned
                            + " bao (lệch " + Math.round(deviation * 10) / 10.0 + "%, ngưỡng "
                            + limit.stripTrailingZeros().toPlainString() + "%)", false));
                }
            }
            return items;
        });
    }

    /** Dừng máy: dừng tự đóng cuối ca và dừng kỹ thuật chưa xác nhận → cảnh báo (OEE giữ tạm tính). */
    @Bean
    ShiftCloseCheck downtimeCheck(JdbcTemplate jdbc, MasterdataQueries masterdata) {
        return simple("CHK_DOWNTIME", (sessionId, date, shift) -> {
            List<Map<String, Object>> stops = jdbc.queryForList("SELECT BIN_TO_UUID(s.machine_id) AS machineId, "
                    + "s.reason_code, s.end_at, s.auto_closed, s.tech_confirmed_at FROM pr_stop_event s JOIN "
                    + "pr_machine_run r ON r.id = s.run_id WHERE r.session_id = UUID_TO_BIN(?)",
                    sessionId.toString());
            Map<UUID, MachineInfo> machines = masterdata.machines(stops.stream()
                    .map(r -> UUID.fromString((String) r.get("machineId"))).distinct().toList());
            List<Item> items = new ArrayList<>();
            for (Map<String, Object> s : stops) {
                UUID machineId = UUID.fromString((String) s.get("machineId"));
                String lbl = label(machines, machineId);
                String reason = (String) s.get("reason_code");
                if (s.get("end_at") == null) {
                    items.add(new Item("CHK_DOWNTIME", machineId + ":" + reason + ":open", lbl, Level.BLOCK,
                            "Máy " + lbl + " còn sự kiện dừng " + reason + " chưa kết thúc", false));
                    continue;
                }
                if (Boolean.TRUE.equals(s.get("auto_closed"))) {
                    items.add(new Item("CHK_DOWNTIME", machineId + ":" + reason + ":auto", lbl, Level.WARN,
                            "Máy " + lbl + ": dừng " + reason + " tự đóng lúc hết ca, cần xác nhận thời gian", false));
                }
                if (s.get("tech_confirmed_at") == null && masterdata.stopReason(reason)
                        .map(sr -> sr.requiresTechConfirm()).orElse(false)) {
                    items.add(new Item("CHK_DOWNTIME", machineId + ":" + reason + ":tech", lbl, Level.WARN,
                            "Máy " + lbl + ": dừng " + reason + " chưa có xác nhận kỹ thuật — OEE giữ tạm tính",
                            false));
                }
            }
            return items;
        });
    }

    /** Thiết bị còn lệnh chưa đồng bộ hoặc xung đột PR còn mở → chặn chốt. */
    @Bean
    ShiftCloseCheck unsyncedCheck(DeviceRegistry devices, SyncQueries sync) {
        return simple("CHK_UNSYNCED", (sessionId, date, shift) -> {
            List<Item> items = new ArrayList<>();
            for (DeviceRegistry.DeviceInfo d : devices.devices()) {
                if (d.active() && d.pendingCount() > 0) {
                    items.add(new Item("CHK_UNSYNCED", d.id().toString(), d.name(), Level.BLOCK,
                            "Thiết bị " + d.name() + " còn " + d.pendingCount() + " lệnh chưa đồng bộ", false));
                }
            }
            long conflicts = sync.openConflicts("PR.");
            if (conflicts > 0) {
                items.add(new Item("CHK_UNSYNCED", "conflicts", "Xung đột đồng bộ", Level.BLOCK,
                        "Còn " + conflicts + " xung đột đồng bộ PR chưa xử lý", false));
            }
            return items;
        });
    }

    @Bean
    ShiftCloseCheck openAlertCheck(AlertQueries alerts) {
        return simple("CHK_OPEN_ALERT", (sessionId, date, shift) -> alerts.openCriticalForSession(sessionId).stream()
                .map(a -> new Item("CHK_OPEN_ALERT", a.id().toString(), a.ruleCode(), Level.EXPLAIN,
                        "Cảnh báo CRITICAL chưa xử lý: " + a.title(), false)).toList());
    }

    private interface Evaluator {
        List<Item> run(UUID sessionId, LocalDate date, String shift);
    }

    private static ShiftCloseCheck simple(String code, Evaluator evaluator) {
        return new ShiftCloseCheck() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public List<Item> evaluate(UUID sessionId, LocalDate businessDate, String shiftCode) {
                return evaluator.run(sessionId, businessDate, shiftCode);
            }
        };
    }
}
