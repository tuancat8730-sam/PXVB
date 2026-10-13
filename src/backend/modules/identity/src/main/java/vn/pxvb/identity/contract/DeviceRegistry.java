package vn.pxvb.identity.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Hợp đồng công khai về thiết bị đã đăng ký (dùng cho đồng bộ và giám sát). */
public interface DeviceRegistry {

    record DeviceInfo(UUID id, String code, String name, boolean active, Instant lastSeenAt, Instant lastSyncAt,
                      int pendingCount, String appVersion, Long clockOffsetMs) {
    }

    /** Ghi nhận một lần đồng bộ: thời điểm, số lệnh còn chờ, lệch đồng hồ (ms), phiên bản app. */
    void recordSync(UUID deviceId, int pendingCount, Long clockOffsetMs, String appVersion);

    List<DeviceInfo> devices();
}
