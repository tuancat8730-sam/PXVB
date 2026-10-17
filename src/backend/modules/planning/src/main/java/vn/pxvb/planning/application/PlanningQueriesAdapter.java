package vn.pxvb.planning.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.planning.contract.PlanningQueries;

/** Hiện thực {@link PlanningQueries}: PR không đọc bảng {@code pl_*} trực tiếp. */
@Service
public class PlanningQueriesAdapter implements PlanningQueries {

    private final JdbcTemplate jdbc;

    public PlanningQueriesAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderRef> activeOrders(LocalDate businessDate, String shiftCode) {
        return jdbc.query("SELECT BIN_TO_UUID(id) AS id, code, shift_code, BIN_TO_UUID(maker_machine_id) AS maker, "
                + "BIN_TO_UUID(packer_machine_id) AS packer, BIN_TO_UUID(product_id) AS product, planned_packs, "
                + "status FROM pl_production_order WHERE business_date = ? AND shift_code = ? "
                + "AND status IN ('CONFIRMED','RUNNING') ORDER BY code", (rs, i) -> new OrderRef(
                UUID.fromString(rs.getString("id")), rs.getString("code"), rs.getString("shift_code"),
                UUID.fromString(rs.getString("maker")), UUID.fromString(rs.getString("packer")),
                UUID.fromString(rs.getString("product")), rs.getLong("planned_packs"), rs.getString("status")),
                businessDate, shiftCode);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RosterRef> roster(LocalDate businessDate, String shiftCode) {
        return jdbc.query("SELECT BIN_TO_UUID(id) AS id, status, BIN_TO_UUID(leader_employee_id) AS leader "
                + "FROM pl_shift_roster WHERE business_date = ? AND shift_code = ?", (rs, i) -> new RosterRef(
                UUID.fromString(rs.getString("id")), rs.getString("status"),
                rs.getString("leader") == null ? null : UUID.fromString(rs.getString("leader"))),
                businessDate, shiftCode).stream().findFirst();
    }

    @Override
    @Transactional
    public void markRunning(UUID orderId) {
        transition(orderId, "CONFIRMED", "RUNNING");
    }

    @Override
    @Transactional
    public void markCompleted(UUID orderId) {
        transition(orderId, "RUNNING", "COMPLETED");
    }

    private void transition(UUID orderId, String from, String to) {
        jdbc.update("UPDATE pl_production_order SET status = ?, row_version = row_version + 1, "
                + "updated_at = UTC_TIMESTAMP(6) WHERE id = UUID_TO_BIN(?) AND status = ?", to,
                orderId.toString(), from);
    }
}
