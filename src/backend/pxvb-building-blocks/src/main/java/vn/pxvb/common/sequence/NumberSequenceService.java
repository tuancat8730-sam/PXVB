package vn.pxvb.common.sequence;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sinh mã chứng từ tuần tự an toàn đồng thời (SDD §3 sys.NumberSequence): {@code <prefix>-<kỳ>-<nnn>}
 * ví dụ {@code LSX-20261013-001}. Khóa dòng bằng {@code FOR UPDATE}; reset theo chính sách NONE/YEARLY/MONTHLY/DAILY.
 */
@Service
public class NumberSequenceService {

    public enum ResetPolicy { NONE, YEARLY, MONTHLY, DAILY }

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public NumberSequenceService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public String next(String key, String prefix, ResetPolicy policy) {
        jdbc.update("INSERT IGNORE INTO sys_number_sequence(seq_key, prefix, last_number, reset_policy, period_key) "
                + "VALUES (?, ?, 0, ?, '')", key, prefix, policy.name());
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT prefix, last_number, reset_policy, period_key FROM sys_number_sequence "
                        + "WHERE seq_key = ? FOR UPDATE", key);
        Map<String, Object> row = rows.get(0);
        ResetPolicy effective = ResetPolicy.valueOf((String) row.get("reset_policy"));
        String period = periodKey(effective);
        long last = ((Number) row.get("last_number")).longValue();
        if (!period.equals(row.get("period_key"))) {
            last = 0;
        }
        long next = last + 1;
        jdbc.update("UPDATE sys_number_sequence SET last_number = ?, period_key = ? WHERE seq_key = ?",
                next, period, key);
        String seq = String.format("%03d", next);
        String p = (String) row.get("prefix");
        return period.isEmpty() ? p + "-" + seq : p + "-" + period + "-" + seq;
    }

    private String periodKey(ResetPolicy policy) {
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        return switch (policy) {
            case NONE -> "";
            case YEARLY -> today.format(DateTimeFormatter.ofPattern("yyyy"));
            case MONTHLY -> today.format(DateTimeFormatter.ofPattern("yyyyMM"));
            case DAILY -> today.format(DateTimeFormatter.BASIC_ISO_DATE);
        };
    }
}
