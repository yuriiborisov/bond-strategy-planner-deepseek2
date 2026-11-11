package com.bondplanner.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Простое ограничение частоты запросов (§11.1): 10 расчётов/мин на пользователя,
 * 60 запросов/мин на IP. In-memory, single-node (v1); при масштабировании — Redis.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int USER_LIMIT_PER_MINUTE = 10;
    private static final int IP_LIMIT_PER_MINUTE = 60;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private record Window(long startedAt, AtomicInteger counter) {
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !(path.startsWith("/api/v1/calculations") || path.startsWith("/api/v1/ai/"));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        long now = System.currentTimeMillis();
        String ip = clientIp(request);
        if (!allow("ip:" + ip, IP_LIMIT_PER_MINUTE, now)) {
            reject(response, "Слишком много запросов с этого IP");
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser user) {
            if (!allow("user:" + user.id(), USER_LIMIT_PER_MINUTE, now)) {
                reject(response, "Слишком много расчётов: лимит " + USER_LIMIT_PER_MINUTE + " в минуту");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean allow(String key, int limit, long now) {
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startedAt() > Duration.ofMinutes(1).toMillis()) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.counter().incrementAndGet();
            return existing;
        });
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() > 600_000);
        }
        return window.counter().get() <= limit;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void reject(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"https://bondplanner.local/errors/rate-limited","title":"Rate limited",
                 "status":429,"code":"RATE_LIMITED","detail":"%s"}
                """.formatted(detail.replace("\"", "'")));
    }
}
