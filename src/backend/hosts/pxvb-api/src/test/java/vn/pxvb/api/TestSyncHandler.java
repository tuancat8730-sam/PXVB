package vn.pxvb.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import vn.pxvb.sync.contract.SyncCommandHandler;

/** Handler giả: bản ghi có phiên bản; baseVersion lệch → xung đột (trừ khi force). */
@Component
class TestSyncHandler implements SyncCommandHandler {

    private record Row(long version, String value) {
    }

    private final ObjectMapper mapper;
    private final Map<UUID, Row> rows = new ConcurrentHashMap<>();

    TestSyncHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String type() {
        return "TEST.Set";
    }

    long versionOf(UUID id) {
        Row row = rows.get(id);
        return row == null ? 0 : row.version();
    }

    String valueOf(UUID id) {
        Row row = rows.get(id);
        return row == null ? null : row.value();
    }

    void seed(UUID id, String value) {
        rows.put(id, new Row(1, value));
    }

    @Override
    public JsonNode apply(Command command, boolean force) {
        String value = command.payload() == null ? null : command.payload().path("value").asText(null);
        if ("INVALID".equals(value)) {
            throw new RejectedException("Giá trị không hợp lệ");
        }
        Row current = rows.get(command.entityId());
        long currentVersion = current == null ? 0 : current.version();
        if (!force && command.baseVersion() != null && command.baseVersion() != currentVersion) {
            throw new ConflictException("Bản ghi đã được thay đổi trên máy chủ (phiên bản " + currentVersion + ")",
                    mapper.valueToTree(Map.of("version", currentVersion,
                            "value", current == null ? "" : current.value())));
        }
        rows.put(command.entityId(), new Row(currentVersion + 1, value));
        return mapper.valueToTree(Map.of("version", currentVersion + 1));
    }
}
