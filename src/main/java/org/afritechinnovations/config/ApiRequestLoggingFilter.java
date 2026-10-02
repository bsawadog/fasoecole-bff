package org.afritechinnovations.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Logs API access without recording request bodies, query parameters, or headers. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String method = request.getMethod();
        String path = request.getRequestURI().substring(request.getContextPath().length());

        try {
            filterChain.doFilter(request, response);
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("{} {} -> {} ({} ms)", method, path, response.getStatus(), elapsedMs);
        } catch (IOException | ServletException | RuntimeException ex) {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.warn("{} {} failed after {} ms ({})", method, path, elapsedMs,
                    ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
