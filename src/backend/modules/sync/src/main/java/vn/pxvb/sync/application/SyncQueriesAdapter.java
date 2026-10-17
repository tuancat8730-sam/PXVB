package vn.pxvb.sync.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.sync.contract.SyncQueries;

@Service
public class SyncQueriesAdapter implements SyncQueries {

    private final JdbcTemplate jdbc;

    public SyncQueriesAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public long openConflicts(String commandTypePrefix) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM sys_sync_conflict WHERE status = 'OPEN' AND "
                + "command_type LIKE ?", Long.class, commandTypePrefix.replace("%", "") + "%");
        return n == null ? 0 : n;
    }
}
