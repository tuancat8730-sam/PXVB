package vn.pxvb.sync.contract;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * SPI: module nghiệp vụ xử lý một loại lệnh từ tablet (vd {@code PR.RecordOutput}, {@code MT.ConfirmReceipt}).
 * Engine sync lo thứ tự, idempotency theo {@code (deviceId, clientCommandId)}, ghi nhận kết quả và xung đột.
 * Handler không được tự bắt idempotency; chỉ cần kiểm tra nghiệp vụ và ném {@link RejectedException} /
 * {@link ConflictException} (hoặc BusinessException DATA_LOCKED/CONFLICT, được map thành xung đột).
 */
public interface SyncCommandHandler {

    record Command(UUID clientCommandId, String type, UUID entityId, JsonNode payload, Instant occurredAt,
                   Instant enteredAt, UUID userId, UUID deviceId, Long baseVersion, UUID shiftSessionId,
                   UUID machineId) {
    }

    String type();

    /**
     * @param force {@code true} khi người có thẩm quyền chọn "chấp nhận bản tablet" để giải quyết xung đột:
     *              bỏ qua kiểm tra {@code baseVersion}.
     * @return dữ liệu kết quả trả về cho tablet (tùy chọn)
     */
    JsonNode apply(Command command, boolean force);

    /** Lệnh không hợp lệ về nghiệp vụ, không thể thử lại. */
    class RejectedException extends RuntimeException {
        public RejectedException(String message) {
            super(message);
        }
    }

    /** Dữ liệu trên máy chủ đã khác so với {@code baseVersion} của tablet. */
    class ConflictException extends RuntimeException {
        private final transient JsonNode serverSnapshot;

        public ConflictException(String message, JsonNode serverSnapshot) {
            super(message);
            this.serverSnapshot = serverSnapshot;
        }

        public JsonNode serverSnapshot() {
            return serverSnapshot;
        }
    }
}
