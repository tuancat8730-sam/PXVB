package vn.pxvb.masterdata.application;

import com.github.f4b6a3.uuid.UuidCreator;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dữ liệu khởi tạo (SDD §5.5), idempotent. Các giá trị đánh dấu ⚠ là giả định để chạy thử (Q04, Q05, Q19):
 * giờ ca, khung cấp phát chia đều 25%, mã dừng A–I theo thứ tự liệt kê, ngưỡng mặc định. Nghiệp vụ TLSG xác nhận
 * rồi chỉnh qua API/Excel.
 */
@Component
public class MasterdataSeeder implements ApplicationRunner {

    private record Threshold(String metric, String value, String unit) {
    }

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final String COMMON = "id, row_version, created_at, updated_at";
    private static final String COMMON_VALUES = "UUID_TO_BIN(?), 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)";

    private static final List<Threshold> THRESHOLDS = List.of(
            new Threshold("PR_OUTPUT_BEHIND_PCT", "15", "%"), new Threshold("PR_STOP_LONG_MIN", "30", "phút"),
            new Threshold("PR_OEE_LOW_PCT", "60", "%"), new Threshold("PL_WEEK_VS_MONTH_PCT", "10", "%"),
            new Threshold("MT_ISSUE_OVER_SLIP_PCT", "10", "%"), new Threshold("MT_SLOT_TOLERANCE_MIN", "30", "phút"),
            new Threshold("MT_WASTE_PCT", "3", "%"), new Threshold("QC_CHECK_INTERVAL_MIN", "30", "phút"),
            new Threshold("QC_LATE_ESCALATE_MIN", "10", "phút"), new Threshold("QC_GRADE_A_SLA_MIN", "30", "phút"),
            new Threshold("TX_MISMATCH_TOL", "0", "tem"), new Threshold("PM_DUE_SOON_DAYS", "3", "ngày"),
            new Threshold("PM_REPAIR_APPROVAL_SLA_MIN", "15", "phút"), new Threshold("OH_REPEAT_COUNT", "3", "lần"),
            new Threshold("OH_REPEAT_MONTHS", "12", "tháng"), new Threshold("SYNC_STALE_MIN", "30", "phút"),
            new Threshold("OUTPUT_INTERVAL_MIN", "120", "phút"));

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public MasterdataSeeder(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        tx.executeWithoutResult(status -> seed());
    }

    void seed() {
        simple("md_material_category", "FIBER", "Nguyên liệu (sợi)");
        simple("md_material_category", "ROLLING", "Vật tư vấn");
        simple("md_material_category", "PACKING", "Vật tư bao");
        simple("md_overhaul_type_def", "BT", "Bảo trì");
        simple("md_overhaul_type_def", "TT", "Trung tu");
        simple("md_overhaul_type_def", "DT", "Đại tu");

        shift("A", "Ca A", "07:00", "15:00", false, 1);
        shift("B", "Ca B", "15:00", "23:00", false, 2);
        shift("C", "Ca C", "23:00", "07:00", true, 3);

        stop("A", "Đổi mác thuốc", "PLANNED", false, false, false, 1);
        stop("B", "Vệ sinh bảo trì", "PLANNED", false, false, false, 2);
        stop("C", "Máy hỏng", "TECHNICAL", false, true, true, 3);
        stop("D", "Không ổn định", "TECHNICAL", false, true, true, 4);
        stop("E", "Giờ ăn", "PLANNED", true, false, false, 5);
        stop("F", "Thiếu điếu/bao", "MATERIAL", false, false, false, 6);
        stop("G", "Không có yêu cầu sản xuất", "PLANNED", true, false, false, 7);
        stop("H", "Thiếu NVL", "MATERIAL", false, false, false, 8);
        stop("I", "Lý do khác", "OTHER", false, false, false, 9);

        reason("STAMP_WASTE", "JAM", "Tem kẹt máy");
        reason("STAMP_WASTE", "MISALIGNED", "Dán lệch");
        reason("STAMP_WASTE", "TORN", "Rách");
        reason("STAMP_WASTE", "PRINT_ERROR", "Lỗi in");
        reason("STAMP_WASTE", "OTHER", "Khác");

        for (Threshold t : THRESHOLDS) {
            jdbc.update("INSERT INTO md_threshold(" + COMMON + ", metric_code, scope_type, threshold_value, unit, "
                            + "effective_from, status, change_reason) SELECT " + COMMON_VALUES
                            + ", ?, 'GLOBAL', ?, ?, '2026-01-01', 'ACTIVE', 'Giá trị khởi tạo (giả định, cần xác nhận)' "
                            + "FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM md_threshold WHERE metric_code = ? "
                            + "AND scope_type = 'GLOBAL' AND level IS NULL)",
                    id(), t.metric(), t.value(), t.unit(), t.metric());
        }
    }

    private void simple(String table, String code, String name) {
        jdbc.update("INSERT IGNORE INTO " + table + "(" + COMMON + ", is_active, code, name) VALUES ("
                + COMMON_VALUES + ", 1, ?, ?)", id(), code, name);
    }

    private void shift(String code, String name, String start, String end, boolean crosses, int seq) {
        jdbc.update("INSERT IGNORE INTO md_shift_def(" + COMMON + ", is_active, code, name, start_time, end_time, "
                + "crosses_midnight, seq) VALUES (" + COMMON_VALUES + ", 1, ?, ?, ?, ?, ?, ?)",
                id(), code, name, start + ":00", end + ":00", crosses, seq);
        String[] labels = {"SANG", "TRUA", "CHIEU", "DEM"};
        LocalTime base = LocalTime.parse(start);
        for (int slot = 1; slot <= 4; slot++) {
            LocalTime from = base.plusHours(2L * (slot - 1));
            LocalTime to = base.plusHours(2L * slot);
            jdbc.update("INSERT IGNORE INTO md_issue_slot_def(" + COMMON + ", is_active, shift_code, slot_no, "
                            + "label, start_time, end_time, share_pct) VALUES (" + COMMON_VALUES
                            + ", 1, ?, ?, ?, ?, ?, 25)", id(), code, slot, labels[slot - 1],
                    from.format(HHMM) + ":00", to.format(HHMM) + ":00");
        }
    }

    private void stop(String code, String name, String category, boolean planned, boolean techConfirm,
                      boolean repair, int sort) {
        jdbc.update("INSERT IGNORE INTO md_stop_reason(" + COMMON + ", is_active, code, name, category, "
                        + "counts_as_planned_downtime, requires_tech_confirm, can_create_repair_request, sort_order) "
                        + "VALUES (" + COMMON_VALUES + ", 1, ?, ?, ?, ?, ?, ?, ?)",
                id(), code, name, category, planned, techConfirm, repair, sort);
    }

    private void reason(String group, String code, String name) {
        jdbc.update("INSERT IGNORE INTO md_reason_code(" + COMMON + ", is_active, group_code, code, name) VALUES ("
                + COMMON_VALUES + ", 1, ?, ?, ?)", id(), group, code, name);
    }

    private static String id() {
        return UuidCreator.getTimeOrderedEpoch().toString();
    }
}
