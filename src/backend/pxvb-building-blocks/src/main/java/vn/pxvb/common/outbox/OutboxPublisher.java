package vn.pxvb.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox: ghi sự kiện cùng giao dịch nghiệp vụ, {@link OutboxDispatcher} phát đi sau (SDD §4.6).
 * Bắt buộc có giao dịch đang chạy để sự kiện không bị mất hoặc phát khi nghiệp vụ rollback.
 */
@Component
public class OutboxPublisher {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public OutboxPublisher(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String type, Object payload) {
        try {
            jdbc.update("INSERT INTO sys_outbox_message(id, type, payload, created_at, attempts) "
                            + "VALUES (UUID_TO_BIN(?), ?, ?, UTC_TIMESTAMP(6), 0)",
                    UuidCreator.getTimeOrderedEpoch().toString(), type, mapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Payload outbox không serialize được: " + type, e);
        }
    }
}
