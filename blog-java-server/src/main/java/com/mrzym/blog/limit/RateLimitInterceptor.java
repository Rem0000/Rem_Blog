package com.mrzym.blog.limit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 只对带 @RateLimit 的 handler 生效。
 *
 * 响应完全对齐现网（实测 mrzym.top 的 /links/add）：
 *   HTTP 429
 *   X-Ratelimit-Limit: 5
 *   X-Ratelimit-Remaining: 0
 *   X-Ratelimit-Reset: <epoch 秒>
 *   Retry-After: 60
 *   body: {\n  "message": "新增过于频繁 请稍后再试"\n}
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitStore store;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws IOException {

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimit rule = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rule == null) {
            return true;
        }

        // 与原实现 ctx.request.ip 一致：默认就是 remoteAddress（Node 未开 app.proxy）
        String ip = request.getRemoteAddr();
        RateLimitStore.Decision decision =
                store.acquire(rule.key() + ":" + ip, rule.max(), rule.windowSeconds());

        response.setHeader("X-RateLimit-Limit", String.valueOf(rule.max()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetEpochSecond()));

        if (!decision.allowed()) {
            response.setStatus(429);
            // koa2-ratelimit 的 Retry-After 固定是 interval（窗口长度），不是剩余秒数
            response.setHeader("Retry-After", String.valueOf(rule.windowSeconds()));
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\n  \"message\": \"" + jsonEscape(rule.message()) + "\"\n}");
            return false;
        }
        return true;
    }

    private static String jsonEscape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
