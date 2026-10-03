package org.afritechinnovations.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private final AuthAttemptLimiter limiter;
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !"POST".equals(request.getMethod()) || !(path.startsWith("/api/auth/")
                || path.equals("/api/users/me/email-verification") || path.endsWith("/invitation"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String group = request.getServletPath().endsWith("/login") ? "login" : "links";
        // Ne pas faire confiance à X-Forwarded-For fourni directement par le client.
        if (!limiter.allow(group + ":" + request.getRemoteAddr(), group.equals("login") ? 20 : 10)) {
            response.setStatus(429); response.setHeader("Retry-After", "60");
            response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"status\":429,\"message\":\"Trop de tentatives. Réessayez dans une minute.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
