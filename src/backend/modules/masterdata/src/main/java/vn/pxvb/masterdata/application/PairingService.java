package vn.pxvb.masterdata.application;

import com.github.f4b6a3.uuid.UuidCreator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
import vn.pxvb.common.sequence.NumberSequenceService;
import vn.pxvb.masterdata.contract.MasterdataQueries.PairingLine;

/**
 * Ghép cặp máy vấn – máy bao theo ca (FR-MD-003, BR-MD-002): tại một thời điểm và ca, một máy bao có đúng một máy
 * vấn; một máy vấn có thể cấp cho nhiều máy bao. Bộ ghép cặp có phiên bản + ngày hiệu lực (CC-12).
 */
@Service
public class PairingService {

    public record LineInput(String shiftCode, UUID makerMachineId, UUID packerMachineId, BigDecimal feedRatio) {
    }

    private static final Set<String> SHIFTS = Set.of("A", "B", "C", "ALL");
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JdbcTemplate jdbc;
    private final NumberSequenceService numbers;
    private final AuditLogWriter audit;
    private final CurrentUser currentUser;
    private final Clock clock;

    public PairingService(JdbcTemplate jdbc, NumberSequenceService numbers, AuditLogWriter audit,
                          CurrentUser currentUser, Clock clock) {
        this.jdbc = jdbc;
        this.numbers = numbers;
        this.audit = audit;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // ---------- Truy vấn ----------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, code, effective_from AS effectiveFrom, "
                + "effective_to AS effectiveTo, status, change_reason AS changeReason, row_version AS rowVersion "
                + "FROM md_machine_pairing ORDER BY created_at DESC");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, code, "
                + "effective_from AS effectiveFrom, effective_to AS effectiveTo, status, change_reason AS "
                + "changeReason, row_version AS rowVersion FROM md_machine_pairing WHERE id = UUID_TO_BIN(?)",
                id.toString());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy bộ ghép cặp");
        }
        Map<String, Object> view = new LinkedHashMap<>(rows.get(0));
        List<Map<String, Object>> lines = jdbc.queryForList("SELECT BIN_TO_UUID(l.id) AS id, l.shift_code AS "
                + "shiftCode, BIN_TO_UUID(l.maker_machine_id) AS makerMachineId, mk.code AS makerCode, "
                + "BIN_TO_UUID(l.packer_machine_id) AS packerMachineId, pk.code AS packerCode, "
                + "l.feed_ratio AS feedRatio, mk.status AS makerStatus, pk.status AS packerStatus "
                + "FROM md_machine_pairing_line l JOIN md_machine mk ON mk.id = l.maker_machine_id "
                + "JOIN md_machine pk ON pk.id = l.packer_machine_id WHERE l.pairing_id = UUID_TO_BIN(?) "
                + "ORDER BY pk.code, l.shift_code", id.toString());
        List<String> warnings = new ArrayList<>();
        for (Map<String, Object> l : lines) {
            if (!"ACTIVE".equals(l.get("makerStatus"))) {
                warnings.add("Máy vấn " + l.get("makerCode") + " đang ở trạng thái " + l.get("makerStatus"));
            }
            if (!"ACTIVE".equals(l.get("packerStatus"))) {
                warnings.add("Máy bao " + l.get("packerCode") + " đang ở trạng thái " + l.get("packerStatus"));
            }
        }
        view.put("lines", lines);
        view.put("warnings", warnings);
        return view;
    }

    @Transactional(readOnly = true)
    public List<PairingLine> resolve(LocalDate date, String shiftCode) {
        return jdbc.query("SELECT l.shift_code, BIN_TO_UUID(l.maker_machine_id) AS maker, "
                        + "BIN_TO_UUID(l.packer_machine_id) AS packer, l.feed_ratio FROM md_machine_pairing_line l "
                        + "JOIN md_machine_pairing p ON p.id = l.pairing_id WHERE p.status IN ('ACTIVE','RETIRED') "
                        + "AND p.effective_from <= ? AND (p.effective_to IS NULL OR p.effective_to > ?) "
                        + "AND (l.shift_code = ? OR l.shift_code = 'ALL') ORDER BY p.effective_from DESC",
                (rs, i) -> new PairingLine(rs.getString("shift_code"), UUID.fromString(rs.getString("maker")),
                        UUID.fromString(rs.getString("packer")), rs.getBigDecimal("feed_ratio")),
                date, date, shiftCode);
    }

    // ---------- Soạn thảo ----------

    @Transactional
    public Map<String, Object> create(String code, LocalDate effectiveFrom, String changeReason,
                                      List<LineInput> lines) {
        String finalCode = code == null || code.isBlank()
                ? numbers.next("pairing", "GC", NumberSequenceService.ResetPolicy.NONE) : code.trim();
        UUID id = UuidCreator.getTimeOrderedEpoch();
        try {
            jdbc.update("INSERT INTO md_machine_pairing(id, row_version, created_at, created_by, updated_at, "
                            + "updated_by, code, effective_from, status, change_reason) VALUES (UUID_TO_BIN(?), 0, "
                            + "UTC_TIMESTAMP(6), UUID_TO_BIN(?), UTC_TIMESTAMP(6), UUID_TO_BIN(?), ?, ?, 'DRAFT', ?)",
                    id.toString(), user(), user(), finalCode, effectiveFrom, changeReason);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.MD_DUPLICATE, "Mã bộ ghép cặp đã tồn tại: " + finalCode);
        }
        for (LineInput in : lines) {
            insertLine(id, in);
        }
        audit(id, "CREATE", List.of(new FieldChange("code", null, finalCode),
                new FieldChange("lines", null, lines.size())));
        return get(id);
    }

    /** Thêm một dòng ghép cặp; vi phạm BR-MD-002 trả PXVB-MD-1002 kèm cặp hiện có (AC-MD-001). */
    @Transactional
    public Map<String, Object> addLine(UUID pairingId, LineInput in) {
        requireEditable(pairingId);
        insertLine(pairingId, in);
        audit(pairingId, "ADD_LINE", List.of(new FieldChange("line", null,
                in.shiftCode() + ":" + in.makerMachineId() + "→" + in.packerMachineId())));
        return get(pairingId);
    }

    @Transactional
    public Map<String, Object> removeLine(UUID pairingId, UUID lineId) {
        requireEditable(pairingId);
        int n = jdbc.update("DELETE FROM md_machine_pairing_line WHERE id = UUID_TO_BIN(?) AND pairing_id = UUID_TO_BIN(?)",
                lineId.toString(), pairingId.toString());
        if (n == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy dòng ghép cặp");
        }
        audit(pairingId, "REMOVE_LINE", List.of(new FieldChange("lineId", lineId.toString(), null)));
        return get(pairingId);
    }

    @Transactional
    public Map<String, Object> activate(UUID id) {
        Map<String, Object> view = get(id);
        if (!"DRAFT".equals(view.get("status"))) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Chỉ kích hoạt được bản nháp");
        }
        if (((List<?>) view.get("lines")).isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Bộ ghép cặp chưa có dòng nào");
        }
        LocalDate from = view.get("effectiveFrom") == null ? LocalDate.now(clock.withZone(ZONE))
                : ((java.sql.Date) view.get("effectiveFrom")).toLocalDate();
        jdbc.update("UPDATE md_machine_pairing SET status = 'RETIRED', effective_to = ?, row_version = row_version + 1, "
                        + "updated_at = UTC_TIMESTAMP(6), updated_by = UUID_TO_BIN(?) "
                        + "WHERE status = 'ACTIVE' AND id <> UUID_TO_BIN(?)", from, user(), id.toString());
        jdbc.update("UPDATE md_machine_pairing SET status = 'ACTIVE', effective_from = ?, row_version = row_version + 1, "
                + "updated_at = UTC_TIMESTAMP(6), updated_by = UUID_TO_BIN(?) WHERE id = UUID_TO_BIN(?)",
                from, user(), id.toString());
        audit(id, "ACTIVATE", List.of(new FieldChange("status", "DRAFT", "ACTIVE")));
        return get(id);
    }

    @Transactional
    public Map<String, Object> cloneVersion(UUID id) {
        Map<String, Object> source = get(id);
        List<LineInput> lines = new ArrayList<>();
        for (Object o : (List<?>) source.get("lines")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> l = (Map<String, Object>) o;
            lines.add(new LineInput((String) l.get("shiftCode"), UUID.fromString((String) l.get("makerMachineId")),
                    UUID.fromString((String) l.get("packerMachineId")), (BigDecimal) l.get("feedRatio")));
        }
        return create(null, null, "Nhân bản từ " + source.get("code"), lines);
    }

    /** Kiểm tra thiếu: máy bao đang hoạt động chưa có máy vấn ở từng ca A/B/C của bộ ghép cặp. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> check(UUID id) {
        get(id);
        List<Map<String, Object>> missing = new ArrayList<>();
        List<Map<String, Object>> packers = jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, code FROM md_machine "
                + "WHERE machine_type = 'PACKER' AND status = 'ACTIVE' AND is_active = 1 ORDER BY code");
        for (Map<String, Object> packer : packers) {
            List<String> covered = jdbc.queryForList("SELECT shift_code FROM md_machine_pairing_line "
                    + "WHERE pairing_id = UUID_TO_BIN(?) AND packer_machine_id = UUID_TO_BIN(?)", String.class,
                    id.toString(), packer.get("id"));
            if (covered.contains("ALL")) {
                continue;
            }
            List<String> lacking = new ArrayList<>(List.of("A", "B", "C"));
            lacking.removeAll(covered);
            if (!lacking.isEmpty()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("packerCode", packer.get("code"));
                m.put("missingShifts", lacking);
                missing.add(m);
            }
        }
        return missing;
    }

    // ---------- Nội bộ ----------

    private void insertLine(UUID pairingId, LineInput in) {
        if (in.shiftCode() == null || !SHIFTS.contains(in.shiftCode().toUpperCase())) {
            throw new BusinessException(ErrorCode.VALIDATION, "Ca phải là A, B, C hoặc ALL");
        }
        String shift = in.shiftCode().toUpperCase();
        if (in.makerMachineId() == null || in.packerMachineId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION, "Cần chọn máy vấn và máy bao");
        }
        BigDecimal ratio = in.feedRatio() == null ? BigDecimal.ONE : in.feedRatio();
        if (ratio.signum() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION, "Tỷ lệ cấp điếu phải lớn hơn 0");
        }
        String makerCode = machineCode(in.makerMachineId(), "MAKER", "máy vấn");
        String packerCode = machineCode(in.packerMachineId(), "PACKER", "máy bao");

        List<Map<String, Object>> clashes = jdbc.queryForList("SELECT l.shift_code AS shiftCode, mk.code AS makerCode, "
                + "BIN_TO_UUID(l.maker_machine_id) AS makerId FROM md_machine_pairing_line l "
                + "JOIN md_machine mk ON mk.id = l.maker_machine_id WHERE l.pairing_id = UUID_TO_BIN(?) "
                + "AND l.packer_machine_id = UUID_TO_BIN(?) AND (l.shift_code = ? OR l.shift_code = 'ALL' OR ? = 'ALL')",
                pairingId.toString(), in.packerMachineId().toString(), shift, shift);
        for (Map<String, Object> c : clashes) {
            if (c.get("makerId").equals(in.makerMachineId().toString())) {
                throw new BusinessException(ErrorCode.MD_DUPLICATE, "Máy bao " + packerCode + " đã được ghép với "
                        + makerCode + " ở ca " + c.get("shiftCode"));
            }
            throw new BusinessException(ErrorCode.MD_PAIRING_CONFLICT, "Máy bao " + packerCode
                    + " đã được ghép với máy vấn " + c.get("makerCode") + " ở ca " + c.get("shiftCode")
                    + "; không thể ghép thêm với " + makerCode + " trong cùng ca",
                    Map.of("existingPair", List.of(c.get("makerCode") + " → " + packerCode + " (ca "
                            + c.get("shiftCode") + ")")));
        }
        try {
            jdbc.update("INSERT INTO md_machine_pairing_line(id, pairing_id, shift_code, maker_machine_id, "
                            + "packer_machine_id, feed_ratio) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, "
                            + "UUID_TO_BIN(?), UUID_TO_BIN(?), ?)", UuidCreator.getTimeOrderedEpoch().toString(),
                    pairingId.toString(), shift, in.makerMachineId().toString(), in.packerMachineId().toString(),
                    ratio);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.MD_PAIRING_CONFLICT,
                    "Máy bao " + packerCode + " đã có máy vấn ở ca " + shift);
        }
    }

    private String machineCode(UUID machineId, String expectedType, String label) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT code, machine_type AS type FROM md_machine "
                + "WHERE id = UUID_TO_BIN(?)", machineId.toString());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION, "Không tìm thấy " + label);
        }
        if (!expectedType.equals(rows.get(0).get("type"))) {
            throw new BusinessException(ErrorCode.VALIDATION,
                    "Máy " + rows.get(0).get("code") + " không phải " + label);
        }
        return (String) rows.get(0).get("code");
    }

    private void requireEditable(UUID pairingId) {
        Map<String, Object> view = get(pairingId);
        if ("RETIRED".equals(view.get("status"))) {
            throw new BusinessException(ErrorCode.DATA_LOCKED, "Bộ ghép cặp đã ngừng hiệu lực, hãy nhân bản");
        }
    }

    private String user() {
        return currentUser.userId().map(UUID::toString).orElse(null);
    }

    private void audit(UUID id, String action, List<FieldChange> changes) {
        var principal = currentUser.principal();
        audit.write(new AuditLogWriter.Entry("md", "MachinePairing", id.toString(), action, changes, null,
                principal.map(p -> p.userId()).orElse(null), null, principal.map(p -> p.deviceId()).orElse(null),
                null));
    }
}
