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

}
