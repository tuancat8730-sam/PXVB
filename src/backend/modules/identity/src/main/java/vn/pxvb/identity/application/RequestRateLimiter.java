package vn.pxvb.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Cửa sổ cố định đếm số lần gọi theo khóa (L04, PXVB-CC-0429). Bộ đếm nằm trong bộ nhớ một tiến trình (G4): chuyển
 * sang Redis ở E25.
 */
@Component
public class RequestRateLimiter {

    static final int MAX_TRACKED_KEYS = 10_000;

    private record Window(Instant startedAt, int count) {
    }

    private final int maxRequests;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();

    @Autowired
    public RequestRateLimiter(IdentityProperties props, Clock clock) {
        this(props.rateLimitMaxRequests(), props.rateLimitWindow(), clock);
    }

    RequestRateLimiter(int maxRequests, Duration window, Clock clock) {
        this.maxRequests = maxRequests;
        this.window = window;
        this.clock = clock;
    }

    /** Trả rỗng nếu được phép; ngược lại trả thời gian còn lại đến khi cửa sổ mở lại. */
    public synchronized Optional<Duration> tryAcquire(String key) {
        Instant now = clock.instant();
        Window current = windows.get(key);
        if (current == null || !now.isBefore(current.startedAt().plus(window))) {
            windows.put(key, new Window(now, 1));
            pruneExpired(now);
            return Optional.empty();
        }
        if (current.count() >= maxRequests) {
            return Optional.of(Duration.between(now, current.startedAt().plus(window)));
        }
        windows.put(key, new Window(current.startedAt(), current.count() + 1));
        return Optional.empty();
    }

    private void pruneExpired(Instant now) {
        if (windows.size() <= MAX_TRACKED_KEYS) {
            return;
        }
        windows.values().removeIf(w -> !now.isBefore(w.startedAt().plus(window)));
    }
}
