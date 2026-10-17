package vn.pxvb.production.application;

import static vn.pxvb.production.application.ProductionTime.instant;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.production.contract.ProductionQueries;

@Service
public class ProductionQueriesAdapter implements ProductionQueries {

    private static final String SQL = "SELECT BIN_TO_UUID(id) AS id, business_date, shift_code, status, "
            + "planned_start, planned_end FROM pr_shift_session";

    private final JdbcTemplate jdbc;

    public ProductionQueriesAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
}
