package vn.pxvb.workflow.contract;

import java.util.UUID;
import vn.pxvb.common.domain.RecordState;

/**
 * SPI: module nghiệp vụ đăng ký bean này cho từng {@code entityType} để workflow có thể áp giá trị đã được duyệt
 * (FR-CC-011) mà không phụ thuộc entity/repository của module đó. {@code apply} chạy trong cùng giao dịch với
 * quyết định duyệt; module chịu trách nhiệm kiểm tra trường được phép sửa và tính lại chỉ số phụ thuộc
 * (có thể lắng nghe sự kiện {@code adjustment.applied}).
 */
public interface AdjustmentApplier {

    record Snapshot(String currentValue, RecordState state) {
    }

    String entityType();

    /** Giá trị hiện tại + trạng thái bản ghi; ném BusinessException nếu bản ghi/trường không hợp lệ. */
    Snapshot snapshot(UUID entityId, String fieldPath);

    void apply(UUID entityId, String fieldPath, String newValue);
}
