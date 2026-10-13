package vn.pxvb.planning.application;

import com.github.f4b6a3.uuid.UuidCreator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.audit.AuditLogWriter;
import vn.pxvb.common.audit.AuditLogWriter.FieldChange;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.common.security.CurrentUser;
import vn.pxvb.dataio.application.ExportService;
import vn.pxvb.masterdata.contract.MasterdataQueries;
import vn.pxvb.masterdata.contract.MasterdataQueries.EmployeeInfo;
import vn.pxvb.masterdata.contract.MasterdataQueries.MachineInfo;

/**
 * Bố trí nhân sự theo ca (FR-PL-005, BR-PL-008): sao chép từ ca trước chỉ bằng một thao tác, tạo bản nháp và hiển
 * thị chênh lệch (nhân sự nghỉ/ngừng hoạt động cần thay), cảnh báo nhân sự trùng máy, xuất Excel để in.
 */
@Service
public class RosterService {

    public record AssignmentInput(UUID machineId, String position, UUID employeeId) {
    }

    private static final Set<String> SHIFTS = Set.of("A", "B", "C");
    private static final Set<String> POSITIONS = Set.of("OPERATOR_1", "OPERATOR_2", "MECHANIC", "SUPERVISOR",
            "ELECTRICIAN", "OTHER");

    private final JdbcTemplate jdbc;
    private final MasterdataQueries masterdata;
    private final ExportService exports;
    private final AuditLogWriter audit;
    private final CurrentUser currentUser;

    public RosterService(JdbcTemplate jdbc, MasterdataQueries masterdata, ExportService exports,
                         AuditLogWriter audit, CurrentUser currentUser) {
        this.jdbc = jdbc;
        this.masterdata = masterdata;
        this.exports = exports;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Transactional
    public Map<String, Object> create(LocalDate date, String shift, UUID leaderEmployeeId) {
        if (date == null || shift == null || !SHIFTS.contains(shift.toUpperCase())) {
            throw new BusinessException(ErrorCode.VALIDATION, "Ngày/ca không hợp lệ (ca A, B, C)");
        }
        requireEmployee(leaderEmployeeId);
        UUID id = UuidCreator.getTimeOrderedEpoch();
        try {
            jdbc.update("INSERT INTO pl_shift_roster(id, row_version, created_at, created_by, updated_at, "
                            + "updated_by, business_date, shift_code, leader_employee_id, status) VALUES ("
                            + "UUID_TO_BIN(?), 0, UTC_TIMESTAMP(6), UUID_TO_BIN(?), UTC_TIMESTAMP(6), "
                            + "UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), 'DRAFT')", id.toString(), user(), user(), date,
                    shift.toUpperCase(), leaderEmployeeId == null ? null : leaderEmployeeId.toString());
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.MD_DUPLICATE, "Ca " + shift + " ngày " + date
                    + " đã có bảng bố trí nhân sự");
        }
        audit(id, "CREATE", List.of(new FieldChange("shift", null, date + "/" + shift)));
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(LocalDate date, String shift) {
        StringBuilder sql = new StringBuilder("SELECT BIN_TO_UUID(id) AS id, business_date AS businessDate, "
                + "shift_code AS shiftCode, status FROM pl_shift_roster WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (date != null) {
            sql.append(" AND business_date = ?");
            params.add(date);
        }
        if (shift != null && !shift.isBlank()) {
            sql.append(" AND shift_code = ?");
            params.add(shift.toUpperCase());
        }
        sql.append(" ORDER BY business_date DESC, shift_code LIMIT 200");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, business_date AS "
                + "businessDate, shift_code AS shiftCode, BIN_TO_UUID(leader_employee_id) AS leaderEmployeeId, "
                + "status, BIN_TO_UUID(copied_from_id) AS copiedFromId, row_version AS rowVersion "
                + "FROM pl_shift_roster WHERE id = UUID_TO_BIN(?)", id.toString());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy bảng bố trí nhân sự");
        }
        Map<String, Object> view = new LinkedHashMap<>(rows.get(0));
        List<Map<String, Object>> assignments = jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, "
                + "BIN_TO_UUID(machine_id) AS machineId, position, BIN_TO_UUID(employee_id) AS employeeId "
                + "FROM pl_shift_roster_assignment WHERE roster_id = UUID_TO_BIN(?)", id.toString());
        Map<UUID, MachineInfo> machines = masterdata.machines(assignments.stream()
                .map(a -> UUID.fromString((String) a.get("machineId"))).distinct().toList());
        List<UUID> employeeIds = new ArrayList<>(assignments.stream()
                .map(a -> UUID.fromString((String) a.get("employeeId"))).distinct().toList());
        if (view.get("leaderEmployeeId") != null) {
            employeeIds.add(UUID.fromString((String) view.get("leaderEmployeeId")));
        }
        Map<UUID, EmployeeInfo> employees = masterdata.employees(employeeIds);
        Map<String, Integer> perEmployee = new HashMap<>();
        for (Map<String, Object> a : assignments) {
            MachineInfo m = machines.get(UUID.fromString((String) a.get("machineId")));
            EmployeeInfo e = employees.get(UUID.fromString((String) a.get("employeeId")));
            a.put("machineCode", m == null ? null : m.code());
            a.put("employeeCode", e == null ? null : e.code());
            a.put("employeeName", e == null ? null : e.fullName());
            a.put("employeeActive", e != null && e.active());
            perEmployee.merge((String) a.get("employeeId"), 1, Integer::sum);
        }
        List<String> warnings = new ArrayList<>();
        perEmployee.forEach((emp, n) -> {
            if (n > 1) {
                EmployeeInfo e = employees.get(UUID.fromString(emp));
                warnings.add("Nhân sự " + (e == null ? emp : e.fullName()) + " được bố trí vào " + n + " vị trí");
            }
        });
        assignments.stream().filter(a -> Boolean.FALSE.equals(a.get("employeeActive"))).forEach(a ->
                warnings.add("Nhân sự " + a.get("employeeCode") + " đã ngừng hoạt động, cần thay thế"));
        view.put("assignments", assignments);
        view.put("warnings", warnings);
        return view;
    }

    @Transactional
    public Map<String, Object> replaceAssignments(UUID id, List<AssignmentInput> assignments) {
        requireDraft(id);
        jdbc.update("DELETE FROM pl_shift_roster_assignment WHERE roster_id = UUID_TO_BIN(?)", id.toString());
        insertAssignments(id, assignments);
        touch(id);
        audit(id, "UPDATE_ASSIGNMENTS", List.of(new FieldChange("assignments", null, assignments.size())));
        return get(id);
    }

    /** Sao chép nguyên bảng bố trí của ca trước vào bảng này (bản nháp) và trả chênh lệch cần xử lý. */
    @Transactional
    public Map<String, Object> copyFrom(UUID targetId, UUID sourceId) {
        requireDraft(targetId);
        Map<String, Object> source = get(sourceId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) source.get("assignments");
        jdbc.update("DELETE FROM pl_shift_roster_assignment WHERE roster_id = UUID_TO_BIN(?)", targetId.toString());
        List<AssignmentInput> inputs = rows.stream().map(a -> new AssignmentInput(
                UUID.fromString((String) a.get("machineId")), (String) a.get("position"),
                UUID.fromString((String) a.get("employeeId")))).toList();
        insertAssignments(targetId, inputs);
        jdbc.update("UPDATE pl_shift_roster SET copied_from_id = UUID_TO_BIN(?), leader_employee_id = "
                        + "COALESCE(leader_employee_id, UUID_TO_BIN(?)), row_version = row_version + 1, "
                        + "updated_at = UTC_TIMESTAMP(6) WHERE id = UUID_TO_BIN(?)", sourceId.toString(),
                source.get("leaderEmployeeId"), targetId.toString());
        audit(targetId, "COPY", List.of(new FieldChange("copiedFrom", null, sourceId.toString())));
        Map<String, Object> view = get(targetId);
        List<Map<String, Object>> needReplace = rows.stream()
                .filter(a -> Boolean.FALSE.equals(a.get("employeeActive"))).toList();
        view.put("needReplacement", needReplace);
        return view;
    }

    @Transactional
    public Map<String, Object> publish(UUID id) {
        Map<String, Object> roster = get(id);
        requireDraft(id);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) roster.get("assignments");
        if (roster.get("leaderEmployeeId") == null || assignments.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION,
                    "Cần chọn trưởng ca và bố trí ít nhất một nhân sự trước khi công bố");
        }
        jdbc.update("UPDATE pl_shift_roster SET status = 'PUBLISHED', row_version = row_version + 1, "
                + "updated_at = UTC_TIMESTAMP(6) WHERE id = UUID_TO_BIN(?)", id.toString());
        audit(id, "PUBLISH", List.of(new FieldChange("status", "DRAFT", "PUBLISHED")));
        return get(id);
    }

    @Transactional(readOnly = true)
    public byte[] export(UUID id) {
        Map<String, Object> roster = get(id);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) roster.get("assignments");
        List<List<Object>> rows = assignments.stream().sorted((a, b) -> String.valueOf(a.get("machineCode"))
                .compareTo(String.valueOf(b.get("machineCode")))).map(a -> List.<Object>of(
                a.get("machineCode") == null ? "" : a.get("machineCode"), a.get("position"),
                a.get("employeeCode") == null ? "" : a.get("employeeCode"),
                a.get("employeeName") == null ? "" : a.get("employeeName"))).toList();
        return exports.workbook("Bố trí ca " + roster.get("shiftCode"),
                List.of("Máy", "Vị trí", "Mã nhân viên", "Họ tên"), rows);
    }

    // ---------- Nội bộ ----------

    private void insertAssignments(UUID rosterId, List<AssignmentInput> assignments) {
        List<UUID> machineIds = assignments.stream().map(AssignmentInput::machineId).distinct().toList();
        List<UUID> employeeIds = assignments.stream().map(AssignmentInput::employeeId).distinct().toList();
        Map<UUID, MachineInfo> machines = masterdata.machines(machineIds);
        Map<UUID, EmployeeInfo> employees = masterdata.employees(employeeIds);
        for (AssignmentInput a : assignments) {
            if (!machines.containsKey(a.machineId())) {
                throw new BusinessException(ErrorCode.VALIDATION, "Máy không tồn tại: " + a.machineId());
            }
            if (!employees.containsKey(a.employeeId())) {
                throw new BusinessException(ErrorCode.VALIDATION, "Nhân sự không tồn tại: " + a.employeeId());
            }
            String position = a.position() == null ? "" : a.position().toUpperCase();
            if (!POSITIONS.contains(position)) {
                throw new BusinessException(ErrorCode.VALIDATION, "Vị trí không hợp lệ: " + a.position());
            }
            try {
                jdbc.update("INSERT INTO pl_shift_roster_assignment(id, roster_id, machine_id, position, "
                                + "employee_id) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, "
                                + "UUID_TO_BIN(?))", UuidCreator.getTimeOrderedEpoch().toString(), rosterId.toString(),
                        a.machineId().toString(), position, a.employeeId().toString());
            } catch (DuplicateKeyException e) {
                throw new BusinessException(ErrorCode.MD_DUPLICATE, "Vị trí " + position + " của máy "
                        + machines.get(a.machineId()).code() + " bị lặp");
            }
        }
    }

    private void requireEmployee(UUID employeeId) {
        if (employeeId != null && !masterdata.employees(List.of(employeeId)).containsKey(employeeId)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Trưởng ca không tồn tại trong danh mục nhân sự");
        }
    }

    private void requireDraft(UUID id) {
        Map<String, Object> roster = jdbc.queryForList("SELECT status FROM pl_shift_roster WHERE id = "
                + "UUID_TO_BIN(?)", id.toString()).stream().findFirst().orElseThrow(
                () -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy bảng bố trí nhân sự"));
        if (!"DRAFT".equals(roster.get("status"))) {
            throw new BusinessException(ErrorCode.DATA_LOCKED, "Bảng bố trí đã công bố, không sửa trực tiếp");
        }
    }

    private void touch(UUID id) {
        jdbc.update("UPDATE pl_shift_roster SET row_version = row_version + 1, updated_at = UTC_TIMESTAMP(6), "
                + "updated_by = UUID_TO_BIN(?) WHERE id = UUID_TO_BIN(?)", user(), id.toString());
    }

    private String user() {
        return currentUser.userId().map(UUID::toString).orElse(null);
    }

    private void audit(UUID id, String action, List<FieldChange> changes) {
        var principal = currentUser.principal();
        audit.write(new AuditLogWriter.Entry("pl", "ShiftRoster", id.toString(), action, changes, null,
                principal.map(p -> p.userId()).orElse(null), null, principal.map(p -> p.deviceId()).orElse(null),
                null));
    }
}
