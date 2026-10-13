package vn.pxvb.masterdata.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Báo cáo chất lượng dữ liệu trước go-live (FR-MD-021, mục 7.4): chỉ ra dữ liệu thiếu/sai để chuẩn hóa
 * (SP thiếu định mức, máy thiếu công suất, NVL thiếu nhóm, khung giờ cấp phát không đủ 100%…).
 */
@Service
public class DataQualityService {

    public record Issue(String code, String severity, String message, long count, List<String> samples) {
    }

    public record Report(Instant generatedAt, boolean hasErrors, List<Issue> issues) {
    }

    private static final int SAMPLE_LIMIT = 20;

    private final JdbcTemplate jdbc;

    public DataQualityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Report report() {
        List<Issue> issues = new ArrayList<>();
        check(issues, "MACHINE_NO_RATE", "ERROR", "Máy vấn/bao đang hoạt động thiếu công suất lý thuyết (> 0)",
                "SELECT code FROM md_machine WHERE machine_type IN ('MAKER','PACKER') AND is_active = 1 "
                        + "AND (theoretical_rate IS NULL OR theoretical_rate <= 0) ORDER BY code");
        check(issues, "PRODUCT_NO_NORM", "ERROR", "Sản phẩm đang sản xuất chưa có định mức hiệu lực",
                "SELECT p.code FROM md_product p WHERE p.is_active = 1 AND p.status = 'ACTIVE' AND NOT EXISTS ("
                        + "SELECT 1 FROM md_bom_norm n WHERE n.product_id = p.id AND n.status = 'ACTIVE') "
                        + "ORDER BY p.code");
        check(issues, "PRODUCT_NO_PACK", "WARN", "Sản phẩm thiếu quy cách đóng gói (số điếu/bao)",
                "SELECT code FROM md_product WHERE is_active = 1 AND rods_per_pack IS NULL ORDER BY code");
        check(issues, "PRODUCT_NO_ALLOWED_MACHINE", "WARN", "Sản phẩm chưa khai báo máy được phép chạy",
                "SELECT p.code FROM md_product p WHERE p.is_active = 1 AND NOT EXISTS ("
                        + "SELECT 1 FROM md_product_machine_allowed a WHERE a.product_id = p.id) ORDER BY p.code");
        check(issues, "MATERIAL_NO_GROUP", "WARN", "Nguyên vật liệu thiếu nhóm ngưỡng hao hụt",
                "SELECT code FROM md_material WHERE is_active = 1 AND (group_code IS NULL OR group_code = '') "
                        + "ORDER BY code");
        check(issues, "SLOT_SHARE_NOT_100", "ERROR", "Khung giờ cấp phát của ca có tổng tỷ lệ khác 100%",
                "SELECT shift_code FROM md_issue_slot_def WHERE is_active = 1 GROUP BY shift_code "
                        + "HAVING ABS(SUM(share_pct) - 100) > 0.001 ORDER BY shift_code");
        check(issues, "PACKER_NOT_PAIRED", "WARN", "Máy bao đang hoạt động chưa có máy vấn trong bộ ghép cặp hiệu lực",
                "SELECT m.code FROM md_machine m WHERE m.machine_type = 'PACKER' AND m.is_active = 1 "
                        + "AND m.status = 'ACTIVE' AND NOT EXISTS (SELECT 1 FROM md_machine_pairing_line l "
                        + "JOIN md_machine_pairing p ON p.id = l.pairing_id AND p.status = 'ACTIVE' "
                        + "WHERE l.packer_machine_id = m.id) ORDER BY m.code");
        check(issues, "SPARE_PART_NO_MIN_STOCK", "INFO", "Phụ tùng chưa có mức tồn tối thiểu",
                "SELECT s.part_no FROM md_spare_part s WHERE s.is_active = 1 AND NOT EXISTS ("
                        + "SELECT 1 FROM md_spare_part_min_stock m WHERE m.spare_part_id = s.id) ORDER BY s.part_no");
        boolean errors = issues.stream().anyMatch(i -> "ERROR".equals(i.severity()));
        return new Report(Instant.now(), errors, issues);
    }

    private void check(List<Issue> out, String code, String severity, String message, String sql) {
        List<String> all = jdbc.queryForList(sql, String.class);
        if (!all.isEmpty()) {
            out.add(new Issue(code, severity, message, all.size(),
                    all.subList(0, Math.min(SAMPLE_LIMIT, all.size()))));
        }
    }
}
