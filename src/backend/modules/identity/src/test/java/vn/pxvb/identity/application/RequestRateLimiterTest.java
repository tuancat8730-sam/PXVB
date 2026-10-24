package vn.pxvb.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RequestRateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Instant START = Instant.parse("2026-10-24T00:00:00Z");

    @Test
    void allowsRequestsUpToTheLimitWithinTheWindow() {
        // Arrange
        RequestRateLimiter limiter = new RequestRateLimiter(3, WINDOW, new MutableClock(START));

        // Act
        boolean[] allowed = {
                limiter.tryAcquire("login|10.0.0.1").isEmpty(),
                limiter.tryAcquire("login|10.0.0.1").isEmpty(),
                limiter.tryAcquire("login|10.0.0.1").isEmpty(),
                limiter.tryAcquire("login|10.0.0.1").isEmpty()};

        // Assert
        assertThat(allowed).containsExactly(true, true, true, false);
    }

    @Test
    void reportsTimeUntilTheWindowResets() {
        // Arrange
        MutableClock clock = new MutableClock(START);
        RequestRateLimiter limiter = new RequestRateLimiter(1, WINDOW, clock);
        limiter.tryAcquire("login|10.0.0.1");
        clock.advance(Duration.ofMinutes(5));

        // Act
        Duration retryAfter = limiter.tryAcquire("login|10.0.0.1").orElseThrow();

        // Assert
        assertThat(retryAfter).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void allowsAgainOnceTheWindowHasPassed() {
        // Arrange
        MutableClock clock = new MutableClock(START);
        RequestRateLimiter limiter = new RequestRateLimiter(1, WINDOW, clock);
        limiter.tryAcquire("login|10.0.0.1");
        clock.advance(WINDOW);

        // Act
        boolean allowed = limiter.tryAcquire("login|10.0.0.1").isEmpty();

        // Assert
        assertThat(allowed).isTrue();
    }

    @Test
    void countsEachKeyIndependently() {
        // Arrange
        RequestRateLimiter limiter = new RequestRateLimiter(1, WINDOW, new MutableClock(START));
        limiter.tryAcquire("login|10.0.0.1");

        // Act
        boolean otherKeyAllowed = limiter.tryAcquire("login|10.0.0.2").isEmpty();

        // Assert
        assertThat(otherKeyAllowed).isTrue();
    }

    /** Đồng hồ có thể tua để kiểm thử cửa sổ thời gian. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            this.now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
