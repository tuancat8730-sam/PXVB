package vn.pxvb.production.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;

/** Chuyển Instant ↔ DATETIME(6) (kết nối cấu hình UTC) và múi giờ nghiệp vụ (BR-PR-008). */
final class ProductionTime {

    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private ProductionTime() {
    }

    static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    /** DATETIME(6) lưu UTC; driver MySQL trả {@code LocalDateTime} (hoặc {@code Timestamp} tùy truy vấn). */
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
