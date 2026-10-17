package vn.pxvb.masterdata.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.masterdata.application.CatalogService.CatalogChanged;
import vn.pxvb.masterdata.contract.MasterdataQueries;

/**
 * Tra cứu ngưỡng theo ngữ cảnh (BR-MD-008) kèm cache 60 giây, vô hiệu khi danh mục ngưỡng đổi; đồng thời là điểm
 * vào duy nhất của {@link MasterdataQueries} (định mức và ghép cặp ủy quyền sang dịch vụ tương ứng).
 */
@Service
public class ThresholdService implements MasterdataQueries {

    private static final long TTL_MILLIS = 60_000;

    private record Candidate(String scopeType, String scopeId, String scopeValue, BigDecimal value, String unit,
                             String level, LocalDate from) {
    }

    private record Cached(Optional<ThresholdValue> value, long expiresAt) {
    }

    private final JdbcTemplate jdbc;
    private final BomNormService norms;
    private final PairingService pairings;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public ThresholdService(JdbcTemplate jdbc, BomNormService norms, PairingService pairings) {
        this.jdbc = jdbc;
        this.norms = norms;
        this.pairings = pairings;
    }

    @EventListener
    public void onCatalogChanged(CatalogChanged event) {
        if ("thresholds".equals(event.resource())) {
            cache.clear();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ThresholdValue> resolveThreshold(String metric, UUID machineId, UUID productId,
                                                     String materialGroup, String stampTypeCode, String level,
                                                     LocalDate asOf) {
        String key = String.join("|", metric, String.valueOf(machineId), String.valueOf(productId),
                String.valueOf(materialGroup), String.valueOf(stampTypeCode), String.valueOf(level),
                String.valueOf(asOf));
        Cached hit = cache.get(key);
        long now = System.currentTimeMillis();
        if (hit != null && hit.expiresAt() > now) {
            return hit.value();
        }
        Optional<ThresholdValue> resolved = compute(metric, machineId, productId, materialGroup, stampTypeCode,
                level, asOf);
        cache.put(key, new Cached(resolved, now + TTL_MILLIS));
        return resolved;
    }

    private Optional<ThresholdValue> compute(String metric, UUID machineId, UUID productId, String materialGroup,
                                             String stampTypeCode, String level, LocalDate asOf) {
        String model = null;
        if (machineId != null) {
            List<String> models = jdbc.queryForList("SELECT model FROM md_machine WHERE id = UUID_TO_BIN(?)",
                    String.class, machineId.toString());
            model = models.isEmpty() ? null : models.get(0);
        }
        final String machineModel = model;
        List<Candidate> candidates = jdbc.query("SELECT scope_type, BIN_TO_UUID(scope_id) AS scope_id, scope_value, "
                        + "threshold_value, unit, level, effective_from FROM md_threshold WHERE metric_code = ? "
                        + "AND status = 'ACTIVE' AND effective_from <= ? AND (effective_to IS NULL OR effective_to > ?)",
                (rs, i) -> new Candidate(rs.getString("scope_type"), rs.getString("scope_id"),
                        rs.getString("scope_value"), rs.getBigDecimal("threshold_value"), rs.getString("unit"),
                        rs.getString("level"), rs.getDate("effective_from").toLocalDate()),
                metric, asOf, asOf);
        return candidates.stream()
                .filter(c -> level == null ? c.level() == null : level.equals(c.level()))
                .filter(c -> rank(c, machineId, productId, machineModel, materialGroup, stampTypeCode) > 0)
                .min(Comparator.<Candidate>comparingInt(
                                c -> rank(c, machineId, productId, machineModel, materialGroup, stampTypeCode))
                        .thenComparing(Candidate::from, Comparator.reverseOrder()))
                .map(c -> new ThresholdValue(c.value(), c.unit(), c.scopeType(), c.level()));
    }

    /** 1 = MACHINE, 2 = PRODUCT, 3 = MODEL/MATERIAL_GROUP/STAMP_TYPE, 4 = GLOBAL; 0 = không áp dụng. */
    private static int rank(Candidate c, UUID machineId, UUID productId, String model, String group, String stamp) {
        return switch (c.scopeType()) {
            case "MACHINE" -> machineId != null && machineId.toString().equals(c.scopeId()) ? 1 : 0;
            case "PRODUCT" -> productId != null && productId.toString().equals(c.scopeId()) ? 2 : 0;
            case "MODEL" -> model != null && model.equals(c.scopeValue()) ? 3 : 0;
            case "MATERIAL_GROUP" -> group != null && group.equals(c.scopeValue()) ? 3 : 0;
            case "STAMP_TYPE" -> stamp != null && stamp.equals(c.scopeValue()) ? 3 : 0;
            case "GLOBAL" -> 4;
            default -> 0;
        };
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Norm> activeNorm(UUID productId, LocalDate asOf) {
        return norms.active(productId, asOf);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PairingLine> resolvePairing(LocalDate date, String shiftCode) {
        return pairings.resolve(date, shiftCode);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<UUID, MachineInfo> machines(java.util.Collection<UUID> ids) {
        java.util.Map<UUID, MachineInfo> result = new java.util.LinkedHashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbc.query("SELECT BIN_TO_UUID(id) AS id, code, machine_type, status, is_active, model FROM md_machine "
                + "WHERE id IN (" + inList(ids.size()) + ")", rs -> {
            result.put(UUID.fromString(rs.getString("id")), new MachineInfo(UUID.fromString(rs.getString("id")),
                    rs.getString("code"), rs.getString("machine_type"), rs.getString("status"),
                    rs.getBoolean("is_active"), rs.getString("model")));
        }, uuidArgs(ids));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<UUID, ProductInfo> products(java.util.Collection<UUID> ids) {
        java.util.Map<UUID, ProductInfo> result = new java.util.LinkedHashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbc.query("SELECT BIN_TO_UUID(id) AS id, code, name, is_active, rods_per_pack FROM md_product "
                + "WHERE id IN (" + inList(ids.size()) + ")", rs -> {
            int rods = rs.getInt("rods_per_pack");
            result.put(UUID.fromString(rs.getString("id")), new ProductInfo(UUID.fromString(rs.getString("id")),
                    rs.getString("code"), rs.getString("name"), rs.getBoolean("is_active"),
                    rs.wasNull() ? null : rods));
        }, uuidArgs(ids));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<UUID, EmployeeInfo> employees(java.util.Collection<UUID> ids) {
        java.util.Map<UUID, EmployeeInfo> result = new java.util.LinkedHashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbc.query("SELECT BIN_TO_UUID(id) AS id, code, full_name, is_active FROM md_employee "
                + "WHERE id IN (" + inList(ids.size()) + ")", rs -> {
            result.put(UUID.fromString(rs.getString("id")), new EmployeeInfo(UUID.fromString(rs.getString("id")),
                    rs.getString("code"), rs.getString("full_name"), rs.getBoolean("is_active")));
        }, uuidArgs(ids));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> productIdByCode(String code) {
        return jdbc.queryForList("SELECT BIN_TO_UUID(id) FROM md_product WHERE code = ?", String.class, code)
                .stream().findFirst().map(UUID::fromString);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Set<UUID> allowedMachineIds(UUID productId) {
        return jdbc.queryForList("SELECT BIN_TO_UUID(machine_id) FROM md_product_machine_allowed "
                + "WHERE product_id = UUID_TO_BIN(?)", String.class, productId.toString()).stream()
                .map(UUID::fromString).collect(java.util.stream.Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MachineInfo> activeMachines() {
        return jdbc.query("SELECT BIN_TO_UUID(id) AS id, code, machine_type, status, is_active, model FROM "
                + "md_machine WHERE is_active = 1 ORDER BY code", (rs, i) -> new MachineInfo(
                UUID.fromString(rs.getString("id")), rs.getString("code"), rs.getString("machine_type"),
                rs.getString("status"), rs.getBoolean("is_active"), rs.getString("model")));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShiftWindow> shift(String code) {
        return jdbc.query("SELECT code, start_time, end_time, crosses_midnight FROM md_shift_def "
                + "WHERE code = ? AND is_active = 1", (rs, i) -> new ShiftWindow(rs.getString("code"),
                rs.getTime("start_time").toLocalTime(), rs.getTime("end_time").toLocalTime(),
                rs.getBoolean("crosses_midnight")), code).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StopReasonInfo> stopReason(String code) {
        return jdbc.query("SELECT code, name, category, counts_as_planned_downtime, requires_tech_confirm, "
                + "can_create_repair_request FROM md_stop_reason WHERE code = ? AND is_active = 1",
                (rs, i) -> new StopReasonInfo(rs.getString("code"), rs.getString("name"),
                        rs.getString("category"), rs.getBoolean("counts_as_planned_downtime"),
                        rs.getBoolean("requires_tech_confirm"), rs.getBoolean("can_create_repair_request")),
                code).stream().findFirst();
    }

    private static String inList(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "UUID_TO_BIN(?)"));
    }

    private static Object[] uuidArgs(java.util.Collection<UUID> ids) {
        return ids.stream().map(UUID::toString).toArray();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean reasonExists(String group, String code) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM md_reason_code WHERE group_code = ? AND code = ? "
                + "AND is_active = 1", Long.class, group, code);
        return n != null && n > 0;
    }

}
