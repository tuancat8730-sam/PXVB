package vn.pxvb.production.application;

import static vn.pxvb.production.application.ProductionTime.ts;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.pxvb.common.audit.AuditLogWriter;
import vn.pxvb.common.audit.AuditLogWriter.FieldChange;
import vn.pxvb.common.outbox.OutboxPublisher;
import vn.pxvb.common.setting.SettingService;

/**
 * Quyết định PO 2026-10-18 (L50): ca {@code CLOSED} tự chuyển {@code FINALIZED} sau N ngày
 * ({@code SHIFT_FINALIZE_DAYS}, mặc định 7). Sau đó không mở lại bằng điều chỉnh cấp ca; chỉ sửa bằng điều chỉnh
 * cấp cao hơn (RP/kế toán).
 */
@Component
public class ShiftFinalizer {

    static final int DEFAULT_DAYS = 7;

    private final JdbcTemplate jdbc;
    private final SettingService settings;
    private final OutboxPublisher outbox;
    private final AuditLogWriter audit;
    private final Clock clock;
    private final boolean enabled;

    public ShiftFinalizer(JdbcTemplate jdbc, SettingService settings, OutboxPublisher outbox, AuditLogWriter audit,
                          Clock clock, @Value("${pxvb.production.scanner.enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.outbox = outbox;
        this.audit = audit;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${pxvb.production.finalizer.interval-ms:3600000}")
    public void scheduled() {
        if (enabled) {
            finalizeDue(clock.instant());
        }
    }

    /** @return số ca đã chuyển sang FINALIZED. */
    @Transactional
    public int finalizeDue(Instant now) {
        int days = Math.max(1, settings.getInt("SHIFT_FINALIZE_DAYS", DEFAULT_DAYS));
        Instant limit = now.minus(Duration.ofDays(days));
        List<Map<String, Object>> due = jdbc.queryForList("SELECT BIN_TO_UUID(id) AS id, shift_code, business_date "
                + "FROM pr_shift_session WHERE status = 'CLOSED' AND closed_at <= ?", ts(limit));
        for (Map<String, Object> s : due) {
            UUID id = UUID.fromString((String) s.get("id"));
            jdbc.update("UPDATE pr_shift_session SET status = 'FINALIZED', row_version = row_version + 1, "
                    + "updated_at = ? WHERE id = UUID_TO_BIN(?) AND status = 'CLOSED'", ts(now), id.toString());
            audit.write(new AuditLogWriter.Entry("pr", "ShiftSession", id.toString(), "FINALIZE", List.of(
                    new FieldChange("status", "CLOSED", "FINALIZED")), "Tự động sau " + days + " ngày", null, null,
                    null, null));
            outbox.publish("pr.shift.finalized", Map.of("sessionId", id.toString(), "businessDate",
                    s.get("business_date").toString(), "shiftCode", s.get("shift_code")));
        }
        return due.size();
    }
}
