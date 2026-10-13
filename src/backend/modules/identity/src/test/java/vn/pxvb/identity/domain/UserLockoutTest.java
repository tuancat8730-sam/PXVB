package vn.pxvb.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserLockoutTest {

    private static final Instant NOW = Instant.parse("2026-10-13T01:00:00Z");
    private static final Duration LOCK = Duration.ofMinutes(15);

    @Test
    void locksAfterFiveConsecutiveFailures() {
        User user = User.create("tuan", "Tuan", "E01", "hash");
        for (int i = 0; i < 4; i++) {
            assertThat(user.registerFailure(NOW, 5, LOCK)).isFalse();
        }
        assertThat(user.registerFailure(NOW, 5, LOCK)).isTrue();
        assertThat(user.isLocked(NOW.plusSeconds(1))).isTrue();
        assertThat(user.isLocked(NOW.plus(LOCK).plusSeconds(1))).isFalse();
    }

    @Test
    void successResetsCounter() {
        User user = User.create("tuan", "Tuan", "E01", "hash");
        user.registerFailure(NOW, 5, LOCK);
        user.registerFailure(NOW, 5, LOCK);
        user.registerSuccess(NOW);
        assertThat(user.getFailedCount()).isZero();
        assertThat(user.getLastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void newUserMustChangePassword() {
        assertThat(User.create("tuan", "Tuan", null, "hash").isMustChangePassword()).isTrue();
    }
}
