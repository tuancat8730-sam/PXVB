package vn.pxvb.production.contract;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * SPI bộ kiểm tra chốt ca (CC-11). PR hiện thực sẵn {@code CHK_OUTPUT_COMPLETE/PLAN}, {@code CHK_DOWNTIME},
 * {@code CHK_UNSYNCED}, {@code CHK_OPEN_ALERT}; MT/QC/TX đăng ký bean cho {@code CHK_MATERIAL},
 * {@code CHK_QUALITY}, {@code CHK_STAMP}.
 */
public interface ShiftCloseCheck {

    /**
     * {@code PASS} đạt; {@code WARN} cảnh báo vẫn chốt được; {@code EXPLAIN} cần giải trình mới chốt;
     * {@code BLOCK} chặn chốt (chỉ gỡ được khi {@code approvable} và giải trình đã được duyệt).
     */
    enum Level { PASS, WARN, EXPLAIN, BLOCK }

    record Item(String checkCode, String subjectKey, String subjectLabel, Level level, String message,
                boolean approvable) {
    }

    String code();

    List<Item> evaluate(UUID sessionId, LocalDate businessDate, String shiftCode);
}
