package vn.pxvb.production.application;

import static vn.pxvb.production.application.ProductionTime.instant;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.production.contract.ProductionQueries;

@Service
public class ProductionQueriesAdapter implements ProductionQueries {

    private static final String SQL = "SELECT BIN_TO_UUID(id) AS id, business_date, shift_code, status, "
            + "planned_start, planned_end FROM pr_shift_session";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ProductionQueriesAdapter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private static SessionRef map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SessionRef(UUID.fromString(rs.getString("id")), rs.getDate("business_date").toLocalDate(),
                rs.getString("shift_code"), rs.getString("status"), instant(rs.getObject("planned_start")),
                instant(rs.getObject("planned_end")));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionRef> session(UUID sessionId) {
        return jdbc.query(SQL + " WHERE id = UUID_TO_BIN(?)", (rs, i) -> map(rs), sessionId.toString()).stream()
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SessionRef> session(LocalDate businessDate, String shiftCode) {
        return jdbc.query(SQL + " WHERE business_date = ? AND shift_code = ?", (rs, i) -> map(rs), businessDate,
                shiftCode).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionRef> openSessions() {
        return jdbc.query(SQL + " WHERE status = 'OPEN'", (rs, i) -> map(rs));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> packsByProduct(UUID sessionId, UUID packerMachineId) {
        Map<UUID, Long> out = new LinkedHashMap<>();
        jdbc.query("SELECT BIN_TO_UUID(e.product_id) AS productId, SUM(e.qty) AS qty FROM pr_output_entry e "
                + "JOIN pr_machine_run r ON r.id = e.run_id WHERE r.session_id = UUID_TO_BIN(?) AND "
                + "e.machine_id = UUID_TO_BIN(?) AND e.product_id IS NOT NULL GROUP BY e.product_id", rs -> {
            out.put(UUID.fromString(rs.getString("productId")), rs.getLong("qty"));
        }, sessionId.toString(), packerMachineId.toString());
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionRef> sessionsBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Khoảng ngày không hợp lệ");
        }
        return jdbc.query(SQL + " WHERE business_date BETWEEN ? AND ? ORDER BY business_date, shift_code",
                (rs, i) -> map(rs), from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShiftFacts> shiftFacts(UUID sessionId) {
        List<Map<String, Object>> sessions = jdbc.queryForList("SELECT business_date, shift_code, status, "
                + "actual_end FROM pr_shift_session WHERE id = UUID_TO_BIN(?)", sessionId.toString());
        if (sessions.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> s = sessions.get(0);
        Instant sessionEnd = s.get("actual_end") == null ? null : instant(s.get("actual_end"));

        // Mốc của từng máy = lúc OEE được tính (hoặc lúc kết ca): sản lượng theo segment và dừng máy cùng lấy tại
        // mốc này để một máy không tự mâu thuẫn (tổng segment = totalQty, phút dừng khớp runMinutes).
        Map<UUID, Instant> asOfByRun = new LinkedHashMap<>();
        jdbc.query("SELECT BIN_TO_UUID(run_id) AS run_id, computed_at FROM pr_oee_result WHERE session_id = "
                + "UUID_TO_BIN(?)", rs -> {
            Instant computedAt = instant(rs.getObject("computed_at"));
            asOfByRun.put(UUID.fromString(rs.getString("run_id")), sessionEnd != null ? sessionEnd : computedAt);
        }, sessionId.toString());

        Map<UUID, List<SegmentFact>> segments = new LinkedHashMap<>();
        jdbc.query("SELECT BIN_TO_UUID(e.run_id) AS run_id, e.segment_no, BIN_TO_UUID(e.product_id) AS product_id, "
                + "SUM(e.qty) AS qty, COUNT(*) AS entries FROM pr_output_entry e JOIN pr_oee_result o ON "
                + "o.run_id = e.run_id WHERE o.session_id = UUID_TO_BIN(?) AND e.created_at <= o.computed_at "
                + "GROUP BY e.run_id, e.segment_no, e.product_id ORDER BY e.run_id, e.segment_no", rs -> {
            String product = rs.getString("product_id");
            segments.computeIfAbsent(UUID.fromString(rs.getString("run_id")), k -> new ArrayList<>())
                    .add(new SegmentFact(rs.getInt("segment_no"), product == null ? null : UUID.fromString(product),
                            rs.getLong("qty"), rs.getInt("entries")));
        }, sessionId.toString());

        // Giây dừng theo (run, lý do, loại), cắt tại mốc của máy: sự kiện bắt đầu sau mốc bị bỏ, sự kiện kết thúc sau
        // mốc tính đến mốc (đúng như lúc OEE được tính)
        Map<UUID, Map<String, long[]>> stopTotals = new LinkedHashMap<>();
        jdbc.query("SELECT BIN_TO_UUID(e.run_id) AS run_id, e.reason_code, e.planned_downtime, e.start_at, e.end_at "
                + "FROM pr_stop_event e JOIN pr_oee_result o ON o.run_id = e.run_id WHERE o.session_id = "
                + "UUID_TO_BIN(?) ORDER BY e.run_id, e.start_at", rs -> {
            UUID runId = UUID.fromString(rs.getString("run_id"));
            Instant asOf = asOfByRun.get(runId);
            Instant start = instant(rs.getObject("start_at"));
            if (!start.isBefore(asOf)) {
                return;
            }
            Object endValue = rs.getObject("end_at");
            Instant end = endValue == null ? asOf : instant(endValue);
            if (end.isAfter(asOf)) {
                end = asOf;
            }
            boolean planned = rs.getBoolean("planned_downtime");
            // khóa lý do + loại để cùng mã lý do không trộn dừng kế hoạch và ngoài kế hoạch
            long[] acc = stopTotals.computeIfAbsent(runId, k -> new LinkedHashMap<>()).computeIfAbsent(
                    rs.getString("reason_code") + "|" + (planned ? 1 : 0), k -> new long[2]);
            acc[0] += Math.max(0, Duration.between(start, end).getSeconds());
            acc[1]++;
        }, sessionId.toString());

        List<RunFact> runs = jdbc.query("SELECT BIN_TO_UUID(o.run_id) AS run_id, BIN_TO_UUID(o.machine_id) AS "
                + "machine_id, o.unit, o.planned_minutes, o.run_minutes, o.total_qty, o.good_qty, o.theoretical_qty, "
                + "o.availability, o.performance, o.quality, o.oee, o.is_provisional, BIN_TO_UUID(o.formula_id) AS "
                + "formula_id, f.cap_performance_at_100, o.computed_at, r.planned_packs FROM pr_oee_result o JOIN "
                + "pr_machine_run r ON r.id = o.run_id JOIN pr_oee_formula_config f ON f.id = o.formula_id WHERE "
                + "o.session_id = UUID_TO_BIN(?) ORDER BY o.machine_id", (rs, i) -> {
            UUID runId = UUID.fromString(rs.getString("run_id"));
            List<StopFact> stops = new ArrayList<>();
            long[] seconds = new long[2]; // [kế hoạch, ngoài kế hoạch]; phút = tổng giây / 60 (như RecordingService)
            stopTotals.getOrDefault(runId, Map.of()).forEach((key, acc) -> {
                int sep = key.lastIndexOf('|');
                boolean planned = key.charAt(sep + 1) == '1';
                stops.add(new StopFact(key.substring(0, sep), planned, acc[0], (int) acc[1]));
                seconds[planned ? 0 : 1] += acc[0];
            });
            Object plannedPacks = rs.getObject("planned_packs");
            return new RunFact(UUID.fromString(rs.getString("machine_id")), rs.getString("unit"),
                    plannedPacks == null ? null : ((Number) plannedPacks).longValue(), rs.getInt("planned_minutes"),
                    rs.getInt("run_minutes"), (int) (seconds[0] / 60), (int) (seconds[1] / 60),
                    rs.getLong("total_qty"), rs.getLong("good_qty"), rs.getBigDecimal("theoretical_qty"),
                    rs.getBigDecimal("availability"), rs.getBigDecimal("performance"), rs.getBigDecimal("quality"),
                    rs.getBigDecimal("oee"), rs.getBoolean("is_provisional"),
                    UUID.fromString(rs.getString("formula_id")), rs.getBoolean("cap_performance_at_100"),
                    instant(rs.getObject("computed_at")), List.copyOf(segments.getOrDefault(runId, List.of())),
                    List.copyOf(stops));
        }, sessionId.toString());

        return Optional.of(new ShiftFacts(sessionId, ((java.sql.Date) s.get("business_date")).toLocalDate(),
                (String) s.get("shift_code"), (String) s.get("status"), List.copyOf(runs)));
    }
}
