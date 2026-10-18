package com.mrzym.blog.limit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 全局限流，对应原实现 main.js 里的 `app.use(limiter)`：
 * 每个 IP 每分钟 200 次，超限返回 429 + koa2-ratelimit 的默认文案。
 *
 * 注意：Node 的全局 limiter 没传 message，用的是库默认值
 * "Too many requests, please try again later."，Retry-After 固定为 interval（60s）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class GlobalRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX = 200;
    private static final int WINDOW_SECONDS = 60;
    private static final String MESSAGE = "Too many requests, please try again later.";

    private final RateLimitStore store;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String ip = request.getRemoteAddr();
        RateLimitStore.Decision decision =
                store.acquire("global:" + ip, MAX, WINDOW_SECONDS);

        response.setHeader("X-RateLimit-Limit", String.valueOf(MAX));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetEpochSecond()));

        if (!decision.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(WINDOW_SECONDS));
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"" + MESSAGE + "\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
