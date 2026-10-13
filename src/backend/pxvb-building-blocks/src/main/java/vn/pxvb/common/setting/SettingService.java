package vn.pxvb.common.setting;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tham số hệ thống {@code sys_setting} (SDD §3), ví dụ {@code OUTPUT_INTERVAL_MIN=120}.
 * Giá trị lưu JSON; getter có mặc định để không hard-code ở nơi dùng.
 */
@Service
public class SettingService {

    private static final String GLOBAL = "GLOBAL";

    private final JdbcTemplate jdbc;

    public SettingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> getJson(String key) {
        List<String> rows = jdbc.queryForList(
                "SELECT CAST(setting_value AS CHAR) FROM sys_setting WHERE setting_key = ? AND scope = ?",
                String.class, key, GLOBAL);
        return rows.stream().findFirst();
    }

    public int getInt(String key, int defaultValue) {
        return getJson(key).map(v -> Integer.parseInt(v.replace("\"", "").trim())).orElse(defaultValue);
    }

    @Transactional
    public void putJson(String key, String json) {
        jdbc.update("INSERT INTO sys_setting(setting_key, scope, setting_value, updated_at) "
                + "VALUES (?, ?, CAST(? AS JSON), UTC_TIMESTAMP(6)) "
                + "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value), updated_at = UTC_TIMESTAMP(6)",
                key, GLOBAL, json);
    }
}
