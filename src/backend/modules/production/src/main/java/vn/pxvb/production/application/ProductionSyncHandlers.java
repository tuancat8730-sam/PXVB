package vn.pxvb.production.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.pxvb.production.application.RecordingService.Actor;
import vn.pxvb.production.application.RecordingService.ChangeInput;
import vn.pxvb.production.application.RecordingService.OutputInput;
import vn.pxvb.production.application.RecordingService.StopInput;
import vn.pxvb.sync.contract.SyncCommandHandler;

/**
 * Lệnh tablet của PR (BR-PR-007: thời gian lấy từ {@code OccurredAt}). Idempotency do engine sync lo; việc gửi
 * lại cùng {@code entityId} còn được bảo vệ ở {@link RecordingService}.
 */
final class ProductionSyncHandlers {

    private ProductionSyncHandlers() {
    }

    private static Actor actor(SyncCommandHandler.Command c) {
        return new Actor(c.userId(), c.deviceId(), null);
    }

    private static Long longOf(JsonNode payload, String field) {
        return payload == null || payload.path(field).isMissingNode() || payload.path(field).isNull()
                ? null : payload.path(field).asLong();
    }

    private static Instant instantOf(JsonNode payload, String field) {
        String text = payload == null ? null : payload.path(field).asText(null);
        return text == null || text.isBlank() ? null : Instant.parse(text);
    }

    private static String textOf(JsonNode payload, String field) {
        return payload == null ? null : payload.path(field).asText(null);
    }

    @Component
    static class RecordOutput implements SyncCommandHandler {

        private final RecordingService service;
        private final ObjectMapper mapper;

        RecordOutput(RecordingService service, ObjectMapper mapper) {
            this.service = service;
            this.mapper = mapper;
        }

        @Override
        public String type() {
            return "PR.RecordOutput";
        }

        @Override
        public JsonNode apply(Command c, boolean force) {
            JsonNode p = c.payload();
            return mapper.valueToTree(service.recordOutput(new OutputInput(c.entityId(), c.machineId(),
                    c.shiftSessionId(), longOf(p, "qty"), longOf(p, "counterReading"), instantOf(p, "periodFrom"),
                    instantOf(p, "periodTo"), textOf(p, "note"), c.occurredAt()), actor(c)));
        }
    }

    @Component
    static class StartStop implements SyncCommandHandler {

        private final RecordingService service;
        private final ObjectMapper mapper;

        StartStop(RecordingService service, ObjectMapper mapper) {
            this.service = service;
            this.mapper = mapper;
        }

        @Override
        public String type() {
            return "PR.StartStop";
        }

        @Override
        public JsonNode apply(Command c, boolean force) {
            JsonNode p = c.payload();
            Instant startAt = instantOf(p, "startAt");
            return mapper.valueToTree(service.startStop(new StopInput(c.entityId(), c.machineId(),
                    c.shiftSessionId(), textOf(p, "reasonCode"), startAt == null ? c.occurredAt() : startAt,
                    instantOf(p, "endAt"), textOf(p, "note")), actor(c)));
        }
    }

    @Component
    static class EndStop implements SyncCommandHandler {

        private final RecordingService service;
        private final ObjectMapper mapper;

        EndStop(RecordingService service, ObjectMapper mapper) {
            this.service = service;
            this.mapper = mapper;
        }

        @Override
        public String type() {
            return "PR.EndStop";
        }

        @Override
        public JsonNode apply(Command c, boolean force) {
            Instant endAt = instantOf(c.payload(), "endAt");
            return mapper.valueToTree(service.endStop(c.machineId(), c.shiftSessionId(),
                    endAt == null ? c.occurredAt() : endAt, actor(c)));
        }
    }

    @Component
    static class ChangeProduct implements SyncCommandHandler {

        private final RecordingService service;
        private final ObjectMapper mapper;

        ChangeProduct(RecordingService service, ObjectMapper mapper) {
            this.service = service;
            this.mapper = mapper;
        }

        @Override
        public String type() {
            return "PR.ChangeProduct";
        }

        @Override
        public JsonNode apply(Command c, boolean force) {
            JsonNode p = c.payload();
            String to = textOf(p, "toProductId");
            boolean stopA = p != null && p.path("startStopA").asBoolean(false);
            return mapper.valueToTree(service.changeProduct(new ChangeInput(c.entityId(),
                    c.machineId(), c.shiftSessionId(), to == null ? null : UUID.fromString(to), c.occurredAt(),
                    stopA), actor(c)));
        }
    }
}
