package com.localdelivery.service;

import com.localdelivery.exception.RateLimitException;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Bounded, single-instance throttling. Never trusts a caller's forwarded IP header. */
@Service
public class AuthRateLimiter {
    private static final int MAX_WINDOWS = 10000;
    private record Window(long expiresAt, int count) {}
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    private final boolean enabled;
    public AuthRateLimiter(Clock clock, @Value("${app.accounts.rate-limit-enabled:true}") boolean enabled) {
        this.clock = clock;
        this.enabled = enabled;
    }
    public void login(String email) {
        allow("login:all", 500, Duration.ofMinutes(15));
        allow("login:" + Sha256.hex(email), 10, Duration.ofMinutes(15));
    }
    public void register() { allow("register:all", 30, Duration.ofHours(1)); }
    public void mail(String email) {
        allow("mail:all", 100, Duration.ofHours(1));
        allow("mail:" + Sha256.hex(email), 3, Duration.ofMinutes(15));
    }
    public void token() { allow("token:all", 100, Duration.ofMinutes(15)); }
    synchronized void allow(String key, int limit, Duration duration) {
        if (!enabled) return;
        long now = clock.millis();
        var previous = windows.get(key);
        if (previous != null && previous.expiresAt() <= now) previous = null;
        if (previous == null) {
            // Sweep expired windows only when a new key would exceed the bound.
            if (windows.size() >= MAX_WINDOWS) windows.values().removeIf(window -> window.expiresAt() <= now);
            if (windows.size() >= MAX_WINDOWS) throw new RateLimitException(60);
            previous = new Window(now + duration.toMillis(), 0);
        }
        if (previous.count() >= limit) throw new RateLimitException(Math.max(1, (previous.expiresAt() - now + 999) / 1000));
        windows.put(key, new Window(previous.expiresAt(), previous.count() + 1));
    }
}
