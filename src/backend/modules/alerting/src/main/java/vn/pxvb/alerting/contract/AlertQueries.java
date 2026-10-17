package vn.pxvb.alerting.contract;

import java.util.List;
import java.util.UUID;

/** Truy vấn cảnh báo cho module khác (vd chốt ca kiểm tra cảnh báo CRITICAL của ca chưa xử lý). */
public interface AlertQueries {

    record OpenAlert(UUID id, String ruleCode, String title) {
    }

    /** Cảnh báo CRITICAL của phiên ca còn mở (OPEN/ACK/IN_PROGRESS), chưa RESOLVED/CLOSED. */
    List<OpenAlert> openCriticalForSession(UUID shiftSessionId);
}
