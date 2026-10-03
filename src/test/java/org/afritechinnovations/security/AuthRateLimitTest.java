package org.afritechinnovations.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimitTest {
    @Test
    void limitsAreIndependentAndWindowExpires() {
        class MutableClock extends Clock {
            long now;
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return Instant.ofEpochMilli(now); }
        }
        var clock = new MutableClock();
        var limiter = new AuthAttemptLimiter(clock);
        assertTrue(limiter.allow("login:ip1", 2));
        assertTrue(limiter.allow("login:ip1", 2));
        assertFalse(limiter.allow("login:ip1", 2));
        assertTrue(limiter.allow("login:ip2", 2));
        assertTrue(limiter.allow("links:ip1", 1));
        clock.now = 60_000;
        assertTrue(limiter.allow("login:ip1", 2));
    }

    @Test
    void filterBlocksTwentyFirstLoginWithRetryHeaderAndIgnoresForgedForwardedIp() throws Exception {
        var filter = new AuthRateLimitFilter(new AuthAttemptLimiter());
        var calls = new AtomicInteger();
        for (int i = 0; i < 21; i++) {
            var request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setServletPath("/api/auth/login"); request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For", "10.0.0." + i);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (r, s) -> calls.incrementAndGet());
            if (i == 20) {
                assertEquals(429, response.getStatus()); assertEquals("60", response.getHeader("Retry-After"));
            }
        }
        assertEquals(20, calls.get());
    }

    @Test
    void registrationVerificationAndResetShareTheLinkLimitButBusinessGetsAreUnaffected() throws Exception {
        var filter = new AuthRateLimitFilter(new AuthAttemptLimiter());
        var calls = new AtomicInteger();
        for (int i = 0; i < 11; i++) {
            String path = i % 2 == 0 ? "/api/auth/register" : "/api/auth/forgot-password";
            var request = new MockHttpServletRequest("POST", path); request.setServletPath(path);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (r, s) -> calls.incrementAndGet());
            if (i == 10) assertEquals(429, response.getStatus());
        }
        var get = new MockHttpServletRequest("GET", "/api/users/me"); get.setServletPath("/api/users/me");
        filter.doFilter(get, new MockHttpServletResponse(), (r, s) -> calls.incrementAndGet());
        assertEquals(11, calls.get());
    }
}
