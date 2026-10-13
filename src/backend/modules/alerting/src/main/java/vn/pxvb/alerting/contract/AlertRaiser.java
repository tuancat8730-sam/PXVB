package vn.pxvb.alerting.contract;

import java.util.Map;
import java.util.UUID;

/**
 * Hợp đồng để module nghiệp vụ phát cảnh báo theo mã rule (SDD §4.6). Alerting lo khử trùng, chọn người nhận,
 * leo thang và đẩy realtime; module nghiệp vụ chỉ nêu "rule nào, về đối tượng nào".
 */
public interface AlertRaiser {

    /**
     * @param severity   {@code null} = lấy theo rule; hoặc INFO | WARN | CRITICAL (vd. WARN → CRITICAL khi vượt ngưỡng cao)
     * @param dedupKey   {@code null} = {@code ruleCode:subjectType:subjectId}
     */
    record AlertCommand(String ruleCode, String subjectType, String subjectId, UUID machineId, UUID shiftSessionId,
                        String title, String message, Map<String, Object> payload, String dedupKey,
                        String severity) {
    }

    /** {@code alertId == null} khi rule đang tắt (không phát cảnh báo). */
    record AlertRef(UUID alertId, boolean created) {
    }

    AlertRef raise(AlertCommand command);
}
