package vn.pxvb.common.outbox;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lấy lô sự kiện chưa xử lý bằng {@code SELECT … FOR UPDATE SKIP LOCKED} (nhiều worker không giẫm nhau)
 * rồi chuyển cho các {@link OutboxSink}. Chỉ chạy lịch khi {@code pxvb.outbox.dispatcher.enabled=true}.
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final JdbcTemplate jdbc;
    private final List<OutboxSink> sinks;
    private final boolean enabled;
    private final int batchSize;

    public OutboxDispatcher(JdbcTemplate jdbc, List<OutboxSink> sinks,
                            @Value("${pxvb.outbox.dispatcher.enabled:false}") boolean enabled,
                            @Value("${pxvb.outbox.dispatcher.batch-size:100}") int batchSize) {
        this.jdbc = jdbc;
        this.sinks = sinks;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${pxvb.outbox.dispatcher.interval-ms:1000}")
    public void scheduled() {
        if (enabled) {
            dispatchBatch();
        }
    }

    /** @return số bản ghi đã phát thành công. */
    @Transactional
    public int dispatchBatch() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT BIN_TO_UUID(id) AS id, type, payload FROM sys_outbox_message "
                        + "WHERE processed_at IS NULL ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED",
                batchSize);
        int ok = 0;
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("id");
            try {
                for (OutboxSink sink : sinks) {
                    sink.handle((String) row.get("type"), String.valueOf(row.get("payload")));
                }
                jdbc.update("UPDATE sys_outbox_message SET processed_at = UTC_TIMESTAMP(6), "
                        + "attempts = attempts + 1 WHERE id = UUID_TO_BIN(?)", id);
                ok++;
            } catch (RuntimeException ex) {
                log.warn("Outbox {} phát lỗi, sẽ thử lại: {}", id, ex.toString());
                jdbc.update("UPDATE sys_outbox_message SET attempts = attempts + 1 WHERE id = UUID_TO_BIN(?)", id);
            }
        }
        return ok;
    }
}
