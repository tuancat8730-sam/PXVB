package vn.pxvb.production.application;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.pxvb.common.domain.RecordState;
import vn.pxvb.common.error.BusinessException;
import vn.pxvb.common.error.ErrorCode;
import vn.pxvb.workflow.contract.AdjustmentApplier;

/**
 * Mở lại ca đã chốt chỉ qua {@code AdjustmentRequest} cấp Phó QĐ (FR-CC-083): {@code status} CLOSED → CLOSING để
 * sửa/giải trình và chốt lại (báo cáo ca sinh revision mới).
 */
@Component
class ShiftSessionAdjustmentApplier implements AdjustmentApplier {

    private final JdbcTemplate jdbc;

    ShiftSessionAdjustmentApplier(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String entityType() {
        return "ShiftSession";
    }

    @Override
    public Snapshot snapshot(UUID entityId, String fieldPath) {
        requireField(fieldPath);
        List<String> status = jdbc.queryForList("SELECT status FROM pr_shift_session WHERE id = UUID_TO_BIN(?)",
                String.class, entityId.toString());
        if (status.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên ca");
        }
        RecordState state = switch (status.get(0)) {
            case "CLOSED" -> RecordState.SHIFT_LOCKED;
            case "FINALIZED" -> RecordState.FINALIZED;
            default -> RecordState.DRAFT;
        };
        return new Snapshot(status.get(0), state);
    }

    @Override
    public void apply(UUID entityId, String fieldPath, String newValue) {
        requireField(fieldPath);
        if (!"CLOSING".equals(newValue)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Chỉ được mở lại ca về trạng thái CLOSING");
        }
        int updated = jdbc.update("UPDATE pr_shift_session SET status = 'CLOSING', closed_at = NULL, "
                + "approval_instance_id = NULL, row_version = row_version + 1, updated_at = UTC_TIMESTAMP(6) "
                + "WHERE id = UUID_TO_BIN(?) AND status = 'CLOSED'", entityId.toString());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Chỉ mở lại được ca đã chốt (CLOSED)");
        }
    }

    private static void requireField(String fieldPath) {
        if (!"status".equals(fieldPath)) {
            throw new BusinessException(ErrorCode.VALIDATION, "Trường không được phép điều chỉnh: " + fieldPath);
        }
    }
}
