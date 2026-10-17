package vn.pxvb.alerting.application;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.alerting.contract.AlertQueries;

@Service
public class AlertQueriesAdapter implements AlertQueries {

    private final JdbcTemplate jdbc;

    public AlertQueriesAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OpenAlert> openCriticalForSession(UUID shiftSessionId) {
        return jdbc.query("SELECT BIN_TO_UUID(id) AS id, rule_code, title FROM sys_alert WHERE shift_session_id = "
                + "UUID_TO_BIN(?) AND severity = 'CRITICAL' AND status IN ('OPEN','ACK','IN_PROGRESS') "
                + "ORDER BY raised_at", (rs, i) -> new OpenAlert(UUID.fromString(rs.getString("id")),
                rs.getString("rule_code"), rs.getString("title")), shiftSessionId.toString());
    }
}
