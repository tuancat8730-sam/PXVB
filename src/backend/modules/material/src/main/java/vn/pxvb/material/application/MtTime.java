package vn.pxvb.material.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** DATETIME(6) lưu UTC (kết nối cấu hình UTC); múi giờ nghiệp vụ Asia/Ho_Chi_Minh. */
final class MtTime {

    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private MtTime() {
    }

    static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    static Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime local) {
            return local.toInstant(ZoneOffset.UTC);
        }
        return ((Timestamp) value).toInstant();
    }
}
