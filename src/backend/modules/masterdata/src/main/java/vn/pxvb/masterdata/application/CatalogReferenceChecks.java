package vn.pxvb.masterdata.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** Kiểm tra tham chiếu của luật E08 cần truy vấn DB (FR-MD-012, FR-MD-015, FR-MD-019). */
final class CatalogReferenceChecks {

    private static final String NEW_RECORD_ID = "00000000-0000-0000-0000-000000000000";
    private static final String OPEN_ENDED = "9999-12-31";

    private final JdbcTemplate jdbc;

    CatalogReferenceChecks(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<String> unknownPartNos(Collection<String> partNos) {
        List<String> unknown = new ArrayList<>();
        for (String partNo : partNos) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM md_spare_part WHERE part_no = ?", Integer.class, partNo);
            if (count == null || count == 0) {
                unknown.add(partNo);
            }
        }
        return unknown;
    }

    boolean componentBelongsToMachine(String componentId, String machineId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM md_machine_component WHERE id = UUID_TO_BIN(?) AND machine_id = UUID_TO_BIN(?)",
                Integer.class, componentId, machineId);
        return count != null && count > 0;
    }

    /** Có luật khác cùng lỗi, nhóm áp dụng và cấp, với khoảng hiệu lực giao nhau (không tính chính nó). */
    boolean overlapsExistingGradeRule(Object recordId, String defectCode, String appliesTo, String grade,
            LocalDate from, LocalDate to) {
        String excludeId = recordId == null ? NEW_RECORD_ID : recordId.toString();
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM md_defect_grade_rule
                WHERE defect_code = ? AND applies_to = ? AND grade = ? AND id <> UUID_TO_BIN(?)
                  AND effective_from <= ? AND COALESCE(effective_to, ?) >= ?
                """, Integer.class, defectCode, appliesTo, grade, excludeId,
                to == null ? OPEN_ENDED : to.toString(), OPEN_ENDED, from.toString());
        return count != null && count > 0;
    }
}
