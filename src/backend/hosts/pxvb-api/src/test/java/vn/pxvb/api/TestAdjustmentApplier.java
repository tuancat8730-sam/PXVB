package vn.pxvb.api;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import vn.pxvb.common.domain.RecordState;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.workflow.contract.AdjustmentApplier;

/** Applier giả (in-memory) đóng vai module nghiệp vụ để kiểm thử luồng điều chỉnh sau khóa. */
@Component
class TestAdjustmentApplier implements AdjustmentApplier {

    private record Row(String quantity, RecordState state) {
    }

    private final Map<UUID, Row> rows = new ConcurrentHashMap<>();

    @Override
    public String entityType() {
        return "TestRecord";
    }

    UUID seed(String quantity, RecordState state) {
        UUID id = UUID.randomUUID();
        rows.put(id, new Row(quantity, state));
        return id;
    }

    String quantityOf(UUID id) {
        return rows.get(id).quantity();
    }

    @Override
    public Snapshot snapshot(UUID entityId, String fieldPath) {
        if (!"quantity".equals(fieldPath)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Trường không được phép điều chỉnh: " + fieldPath);
        }
        Row row = rows.get(entityId);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return new Snapshot(row.quantity(), row.state());
    }

    @Override
    public void apply(UUID entityId, String fieldPath, String newValue) {
        Row row = rows.get(entityId);
        rows.put(entityId, new Row(newValue, row.state()));
    }
}
