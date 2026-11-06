package com.fse.fsenotification.security;

import com.fse.fsenotification.common.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Throttles admin password checks (login and change-password) by username and by client IP.
 * <p>
 * Failures are counted within a window of {@code lockout}; reaching the limit blocks that username or IP for
 * {@code lockout} from the last failure. A successful login clears the username's counter.
 * <p>
 * State is in memory, so limits apply per application instance and reset on restart.
 */
@Slf4j
@Component
public class LoginAttemptService {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final ConcurrentMap<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final int maxFailuresPerUsername;
    private final int maxFailuresPerIp;
    private final Duration lockout;
    private final Clock clock;

    @Autowired
    public LoginAttemptService(@Value("${app.security.login.max-failures-per-username:5}") int maxFailuresPerUsername,
                               @Value("${app.security.login.max-failures-per-ip:20}") int maxFailuresPerIp,
                               @Value("${app.security.login.lockout:15m}") Duration lockout) {
        this(maxFailuresPerUsername, maxFailuresPerIp, lockout, Clock.systemUTC());
    }

    LoginAttemptService(int maxFailuresPerUsername, int maxFailuresPerIp, Duration lockout, Clock clock) {
        this.maxFailuresPerUsername = maxFailuresPerUsername;
        this.maxFailuresPerIp = maxFailuresPerIp;
        this.lockout = lockout;
        this.clock = clock;
    }

    /** Throws {@link TooManyRequestsException} if the username or IP is currently locked out. */
    public void checkAllowed(String username, String clientIp) {
        Instant now = clock.instant();
        long retryAfter = Math.max(remainingLock(usernameKey(username), now), remainingLock(ipKey(clientIp), now));
        if (retryAfter > 0) {
            throw new TooManyRequestsException("Too many failed attempts; try again later", retryAfter);
        }
    }

    public void recordFailure(String username, String clientIp) {
        Instant now = clock.instant();
        purgeIfLarge(now);
        if (increment(usernameKey(username), maxFailuresPerUsername, now)) {
            log.warn("Admin username '{}' locked out for {} after repeated failed attempts", username, lockout);
        }
        if (increment(ipKey(clientIp), maxFailuresPerIp, now)) {
            log.warn("IP {} locked out of admin login for {} after repeated failed attempts", clientIp, lockout);
        }
    }

    public void recordSuccess(String username) {
        attempts.remove(usernameKey(username));
    }

    /** @return true if this failure triggered a new lockout */
    private boolean increment(String key, int maxFailures, Instant now) {
        Attempts updated = attempts.compute(key, (k, current) -> {
            if (current == null || current.isStale(now, lockout)) {
                return Attempts.first(now, maxFailures, lockout);
            }
            return current.next(now, maxFailures, lockout);
        });
        return updated.failures() == maxFailures;
    }

    private long remainingLock(String key, Instant now) {
        Attempts current = attempts.get(key);
        if (current == null || current.lockedUntil() == null || !now.isBefore(current.lockedUntil())) {
            return 0;
        }
        return Math.max(1, Duration.between(now, current.lockedUntil()).toSeconds());
    }

    private void purgeIfLarge(Instant now) {
        if (attempts.size() > MAX_TRACKED_KEYS) {
            attempts.values().removeIf(a -> a.isStale(now, lockout));
        }
    }

    private static String usernameKey(String username) {
        return "u:" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

    private static String ipKey(String clientIp) {
        return "ip:" + clientIp;
    }

    private record Attempts(int failures, Instant lastFailure, Instant lockedUntil) {

        static Attempts first(Instant now, int maxFailures, Duration lockout) {
            return new Attempts(1, now, maxFailures <= 1 ? now.plus(lockout) : null);
        }

        Attempts next(Instant now, int maxFailures, Duration lockout) {
            int count = failures + 1;
            return new Attempts(count, now, count >= maxFailures ? now.plus(lockout) : null);
        }

        /** No failure within the window and no active lock: the counter can be reset. */
        boolean isStale(Instant now, Duration lockout) {
            boolean locked = lockedUntil != null && now.isBefore(lockedUntil);
            return !locked && !now.isBefore(lastFailure.plus(lockout));
        }
    }
}
