package org.afritechinnovations.security;

import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Limites locales au serveur, sans conserver les mots de passe ni les courriels. */
@Component
public class AuthAttemptLimiter {
    private record Window(long start, int attempts) {}
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    public AuthAttemptLimiter() { this(Clock.systemUTC()); }
    AuthAttemptLimiter(Clock clock) { this.clock = clock; }
    public synchronized boolean allow(String key, int limit) {
        long now = clock.millis();
        windows.entrySet().removeIf(entry -> now - entry.getValue().start() >= 60_000);
        Window current = windows.get(key);
        if (current == null) {
            if (windows.size() >= 10_000) return false;
            windows.put(key, new Window(now, 1)); return true;
        }
        if (current.attempts() >= limit) return false;
        windows.put(key, new Window(current.start(), current.attempts() + 1)); return true;
    }
}
