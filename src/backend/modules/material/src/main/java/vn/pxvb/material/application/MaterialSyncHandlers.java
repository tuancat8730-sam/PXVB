package vn.pxvb.material.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.pxvb.material.application.IssueService.Actor;
import vn.pxvb.material.application.IssueService.CountInput;
import vn.pxvb.material.application.IssueService.IssueInput;
import vn.pxvb.material.application.IssueService.ReceiptInput;
import vn.pxvb.material.application.IssueService.ReturnInput;
import vn.pxvb.sync.contract.SyncCommandHandler;

/** Lệnh tablet của MT: thời gian lấy từ {@code OccurredAt}; idempotency do engine sync + id thực thể lo. */
final class MaterialSyncHandlers {

    private MaterialSyncHandlers() {
    }

    private static Actor actor(SyncCommandHandler.Command c) {
        return new Actor(c.userId(), c.deviceId());
    }

    private static String text(JsonNode p, String f) {
        return p == null || p.path(f).isMissingNode() || p.path(f).isNull() ? null : p.path(f).asText();
    }

    private static UUID uuid(JsonNode p, String f) {
        String t = text(p, f);
        return t == null || t.isBlank() ? null : UUID.fromString(t);
    }

    private static BigDecimal dec(JsonNode p, String f) {
        String t = text(p, f);
        return t == null || t.isBlank() ? null : new BigDecimal(t);
    }

    private static LocalDate date(JsonNode p, String f) {
        String t = text(p, f);
        return t == null || t.isBlank() ? null : LocalDate.parse(t);
    }

    @Component
    static class IssueMaterial implements SyncCommandHandler {
        private final IssueService service;
        private final ObjectMapper mapper;

        IssueMaterial(IssueService service, ObjectMapper mapper) {
            this.service = service;
            this.mapper = mapper;
        }

        @Override
        public String type() {
            return "MT.IssueMaterial";
        }

        @Override
        public JsonNode apply(Command c, boolean force) {
            JsonNode p = c.payload();
            return mapper.valueToTree(service.recordIssue(new IssueInput(c.entityId(), uuid(p, "slipLineId"),
                    date(p, "businessDate"), text(p, "shiftCode"), p != null && p.hasNonNull("slotNo")
                    ? p.get("slotNo").asInt() : null, c.machineId() != null ? c.machineId() : uuid(p, "machineId"),
                    uuid(p, "materialId"), text(p, "lotNo"), dec(p, "issuedQty"), text(p, "reasonCode"),
                    text(p, "note"), c.occurredAt()), actor(c)));
        }
    }

    @Component
    static class ConfirmReceipt implements SyncCommandHandler {
        private final IssueService service;
        private final ObjectMapper mapper;

        ConfirmReceipt(IssueService service, ObjectMapper mapper) {
            this.service = service;
            this.mapper = mapper;
        }

        @Override
        public String type() {
            return "MT.ConfirmReceipt";
        }

        @Override
        public JsonNode apply(Command c, boolean force) {
            JsonNode p = c.payload();
            return mapper.valueToTree(service.confirmReceipt(new ReceiptInput(c.entityId(), uuid(p, "issueRecordId"),
                    dec(p, "receivedQty"), dec(p, "remainingBefore"), text(p, "note"), c.occurredAt()), actor(c)));
        }
    }

    @Component
    static class RecordReturn implements SyncCommandHandler {
        private final IssueService service;
        private final ObjectMapper mapper;

        RecordReturn(IssueService service, ObjectMapper mapper) {
            this.service = service;
            this.mapper = mapper;
        }

        @Override
        public String type() {
            return "MT.RecordReturn";
        }

        @Override
        public JsonNode apply(Command c, boolean force) {
            JsonNode p = c.payload();
            return mapper.valueToTree(service.recordReturn(new ReturnInput(c.entityId(), date(p, "businessDate"),
                    text(p, "shiftCode"), c.machineId() != null ? c.machineId() : uuid(p, "machineId"),
                    uuid(p, "materialId"), dec(p, "qty"), text(p, "reason"), c.occurredAt()), actor(c)));
        }
    }

    @Component
    static class RecordStockCount implements SyncCommandHandler {
        private final IssueService service;
        private final ObjectMapper mapper;

        RecordStockCount(IssueService service, ObjectMapper mapper) {
            this.service = service;
            this.mapper = mapper;
        }

        @Override
        public String type() {
            return "MT.RecordStockCount";
        }

        @Override
        public JsonNode apply(Command c, boolean force) {
            JsonNode p = c.payload();
            return mapper.valueToTree(service.recordCount(new CountInput(c.entityId(), date(p, "businessDate"),
                    text(p, "shiftCode"), c.machineId() != null ? c.machineId() : uuid(p, "machineId"),
                    uuid(p, "materialId"), text(p, "kind"), dec(p, "qty"), text(p, "note"), c.occurredAt()),
                    actor(c)));
        }
    }
}
