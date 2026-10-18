package vn.pxvb.material.application;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.material.contract.MaterialQueries;

@Service
public class MaterialQueriesAdapter implements MaterialQueries {

    private final JdbcTemplate jdbc;

    public MaterialQueriesAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsumptionLine> consumption(UUID sessionId) {
        return jdbc.query("SELECT BIN_TO_UUID(consumer_machine_id) AS machine_id, BIN_TO_UUID(material_id) AS "
                + "material_id, opening_qty, issued_qty, returned_qty, closing_qty, counted, actual_consumed, "
                + "theoretical_consumed, waste_qty, waste_pct, threshold_pct, flag, explanation, status "
                + "FROM mt_consumption_summary WHERE session_id = UUID_TO_BIN(?) ORDER BY consumer_machine_id, "
                + "material_id", (rs, i) -> new ConsumptionLine(UUID.fromString(rs.getString("machine_id")),
                UUID.fromString(rs.getString("material_id")), rs.getBigDecimal("opening_qty"),
                rs.getBigDecimal("issued_qty"), rs.getBigDecimal("returned_qty"), rs.getBigDecimal("closing_qty"),
                rs.getBoolean("counted"), rs.getBigDecimal("actual_consumed"),
                rs.getBigDecimal("theoretical_consumed"), rs.getBigDecimal("waste_qty"),
                rs.getBigDecimal("waste_pct"), rs.getBigDecimal("threshold_pct"), rs.getString("flag"),
                rs.getString("explanation"), rs.getString("status")), sessionId.toString());
    }
}
