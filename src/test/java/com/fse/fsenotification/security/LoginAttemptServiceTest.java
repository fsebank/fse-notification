package com.fse.fsenotification.security;

import com.fse.fsenotification.common.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final LoginAttemptService service = new LoginAttemptService(3, 5, Duration.ofMinutes(15), clock);

    @Test
    void locksUsernameAfterMaxFailuresAndUnlocksAfterLockout() {
        failTimes("admin", "10.0.0.1", 3);

        assertThatThrownBy(() -> service.checkAllowed("ADMIN", "10.0.0.9"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(900));

        clock.advance(Duration.ofMinutes(15));
        assertThatCode(() -> service.checkAllowed("admin", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void locksIpAcrossDifferentUsernames() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("user" + i, "10.0.0.2");
        }
        assertThatThrownBy(() -> service.checkAllowed("someone-else", "10.0.0.2"))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> service.checkAllowed("someone-else", "10.0.0.3")).doesNotThrowAnyException();
    }

    @Test
    void successResetsUsernameCounter() {
        failTimes("admin", "10.0.0.1", 2);
        service.recordSuccess("admin");
        failTimes("admin", "10.0.0.1", 2);

        assertThatCode(() -> service.checkAllowed("admin", "10.0.0.4")).doesNotThrowAnyException();
    }

    @Test
    void failuresOutsideTheWindowDoNotAccumulate() {
        failTimes("admin", "10.0.0.1", 2);
        clock.advance(Duration.ofMinutes(16));
        failTimes("admin", "10.0.0.1", 2);

        assertThatCode(() -> service.checkAllowed("admin", "10.0.0.4")).doesNotThrowAnyException();
    }

    private void failTimes(String username, String ip, int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(username, ip);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
