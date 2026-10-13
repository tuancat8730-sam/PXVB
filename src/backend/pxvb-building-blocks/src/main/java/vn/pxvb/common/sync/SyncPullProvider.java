package vn.pxvb.common.sync;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * SPI: module cung cấp dữ liệu cho tablet kéo về (FR-CC-062). Đặt ở building-blocks để module dữ liệu
 * (masterdata, …) không phải phụ thuộc module sync. Phân trang bằng khóa (cursor) ổn định; {@code since == null}
 * là tải đầy đủ lần đầu. Tài nguyên không có cờ ngừng/tombstone phải luôn trả đầy đủ.
 */
public interface SyncPullProvider {

    record Page(List<Map<String, Object>> records, String nextCursor) {
    }

    List<String> resources();

    /** Tài nguyên luôn trả đầy đủ (không có tombstone): tablet phải thay thế toàn bộ bản sao cục bộ. */
    default java.util.Set<String> alwaysFull() {
        return java.util.Set.of();
    }

    Page pull(String resource, Instant since, String cursor, int limit);
}
