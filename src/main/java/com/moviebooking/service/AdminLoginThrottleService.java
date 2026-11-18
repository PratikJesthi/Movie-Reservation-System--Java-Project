package com.moviebooking.service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * In-memory brute-force throttle for the admin login endpoint.
 *
 * <p>Keyed by lowercase(email) + "|" + clientIp. Counters reset on application restart.
 * A production deployment should replace this with a shared store (Redis, DB) so that
 * horizontal scaling and pod restarts don't silently clear lockouts.
 */
@Service
public class AdminLoginThrottleService {

    private static final Logger log = LoggerFactory.getLogger(AdminLoginThrottleService.class);

    @Value("${app.admin.login.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.admin.login.window-minutes:15}")
    private long windowMinutes;

    private record Window(int count, Instant windowStart) {}

    private final ConcurrentHashMap<String, Window> counters = new ConcurrentHashMap<>();

    private String key(String email, String ip) {
        return email.trim().toLowerCase() + "|" + ip;
    }

    private long windowSeconds() {
        return windowMinutes * 60L;
    }

    /**
     * Returns true if this email+ip pair is currently locked out.
     * Also prunes an expired window (so the counter resets naturally after the window ends).
     */
    public boolean isLocked(String email, String ip) {
        String k = key(email, ip);
        Window w = counters.get(k);
        if (w == null) return false;
        if (Instant.now().getEpochSecond() - w.windowStart().getEpochSecond() >= windowSeconds()) {
            counters.remove(k);
            return false;
        }
        return w.count() >= maxAttempts;
    }

    /** Record a failed attempt. Returns the new failure count. */
    public int recordFailure(String email, String ip) {
        String k = key(email, ip);
        Window updated = counters.compute(k, (key, w) -> {
            Instant now = Instant.now();
            if (w == null || now.getEpochSecond() - w.windowStart().getEpochSecond() >= windowSeconds()) {
                return new Window(1, now);
            }
            return new Window(w.count() + 1, w.windowStart());
        });
        return updated.count();
    }

    /** Clear the counter after a successful admin login. */
    public void clearOnSuccess(String email, String ip) {
        counters.remove(key(email, ip));
    }

    // Exposed for controller and testing
    public int getMaxAttempts() { return maxAttempts; }
    public long getWindowMinutes() { return windowMinutes; }
}
