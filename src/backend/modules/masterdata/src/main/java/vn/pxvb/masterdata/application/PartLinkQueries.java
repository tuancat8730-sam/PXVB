package vn.pxvb.masterdata.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;

/**
 * Đọc liên kết thiết bị–phụ tùng theo hai chiều (FR-MD-019). Liên kết có {@code machine_id} là gắn trực tiếp máy;
 * liên kết chỉ có {@code model_code} áp cho mọi máy cùng dòng. Phạm vi trả về ghi ở trường {@code scope}.
 */
@Service
public class PartLinkQueries {

    public static final String SCOPE_MACHINE = "MACHINE";
    public static final String SCOPE_MODEL = "MODEL";

    public record PartOfMachine(String linkId, String sparePartId, String partNo, String partName,
                                String componentId, String componentCode, String componentName,
                                String taskTemplateId, BigDecimal typicalQty, String scope) {
    }

    public record MachineOfPart(String machineId, String machineCode, String machineName, String scope) {
    }

    private static final String SCOPE_SQL = "CASE WHEN l.machine_id IS NOT NULL THEN '"
            + SCOPE_MACHINE + "' ELSE '" + SCOPE_MODEL + "' END";

    private final JdbcTemplate jdbc;

    public PartLinkQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<PartOfMachine> partsOfMachine(UUID machineId) {
        requireExists("md_machine", machineId, "Không tìm thấy máy");
        return jdbc.query("""
                SELECT BIN_TO_UUID(l.id) AS linkId, BIN_TO_UUID(sp.id) AS sparePartId, sp.part_no AS partNo,
                       sp.name AS partName, BIN_TO_UUID(c.id) AS componentId, c.code AS componentCode,
                       c.name AS componentName, BIN_TO_UUID(l.task_template_id) AS taskTemplateId,
                       l.typical_qty AS typicalQty, %s AS scope
                FROM md_machine_part_link l
                JOIN md_machine m ON m.id = UUID_TO_BIN(?)
                JOIN md_spare_part sp ON sp.id = l.spare_part_id
                LEFT JOIN md_machine_component c ON c.id = l.component_id
                WHERE l.machine_id = m.id OR (l.machine_id IS NULL AND l.model_code = m.model)
                ORDER BY sp.part_no, l.id
                """.formatted(SCOPE_SQL),
                (rs, i) -> new PartOfMachine(rs.getString("linkId"), rs.getString("sparePartId"),
                        rs.getString("partNo"), rs.getString("partName"), rs.getString("componentId"),
                        rs.getString("componentCode"), rs.getString("componentName"),
                        rs.getString("taskTemplateId"), rs.getBigDecimal("typicalQty"), rs.getString("scope")),
                machineId.toString());
    }

    @Transactional(readOnly = true)
    public List<MachineOfPart> machinesOfPart(UUID sparePartId) {
        requireExists("md_spare_part", sparePartId, "Không tìm thấy phụ tùng");
        return jdbc.query("""
                SELECT DISTINCT BIN_TO_UUID(m.id) AS machineId, m.code AS machineCode, m.name AS machineName,
                       %s AS scope
                FROM md_machine_part_link l
                JOIN md_machine m ON m.id = l.machine_id OR (l.machine_id IS NULL AND m.model = l.model_code)
                WHERE l.spare_part_id = UUID_TO_BIN(?)
                ORDER BY m.code
                """.formatted(SCOPE_SQL),
                (rs, i) -> new MachineOfPart(rs.getString("machineId"), rs.getString("machineCode"),
                        rs.getString("machineName"), rs.getString("scope")),
                sparePartId.toString());
    }

    private void requireExists(String table, UUID id, String message) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = UUID_TO_BIN(?)", Long.class,
                id.toString());
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, message);
        }
    }
}
