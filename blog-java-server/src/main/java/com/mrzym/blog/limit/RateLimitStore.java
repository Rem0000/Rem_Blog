package com.mrzym.blog.limit;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 进程内固定窗口限流器，语义对齐 koa2-ratelimit / ratelimiter：
 *  - 窗口从第一次请求开始计时；
 *  - 窗口内每个请求都计数（含被拒绝的请求）；
 *  - 计数 <= max 放行，否则拒绝，Retry-After 为距窗口结束的秒数（向上取整）。
 *
 * 单机内存实现，够迁移期用；要和 Node 一样多实例共享得换 Redis，不在本次范围。
 */
@Component
public class RateLimitStore {

    /** 超过这个窗口还没被访问的桶可以回收（最长规则是 60 分钟） */
    private static final long STALE_MILLIS = 2 * 60 * 60 * 1000L;

    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public Decision acquire(String key, int max, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMillis = Math.max(windowSeconds, 1) * 1000L;

        Window window = windows.compute(key, (k, old) -> {
            if (old == null || now - old.start >= windowMillis) {
                return new Window(now);
            }
            old.count++;
            return old;
        });

        long resetMillis = window.start + windowMillis;
        int remaining = Math.max(0, max - window.count);
        long retryAfterSeconds = Math.max(1L, (resetMillis - now + 999L) / 1000L);

        sweepIfNeeded(now);
        return new Decision(window.count <= max, remaining, resetMillis / 1000L, retryAfterSeconds);
    }

    private void sweepIfNeeded(long now) {
        if (windows.size() < 1024) {
            return;
        }
        windows.entrySet().removeIf(e -> now - e.getValue().start > STALE_MILLIS);
    }

    private static final class Window {
        private long start;
        private int count;

        private Window(long start) {
            this.start = start;
            this.count = 1;
        }
    }

    /**
     * @param allowed           是否放行
     * @param remaining         本窗口剩余次数
     * @param resetEpochSecond  窗口重置时间（epoch 秒），对应 X-Ratelimit-Reset
     * @param retryAfterSeconds 建议重试等待秒数，对应 Retry-After
     */
    public record Decision(boolean allowed, int remaining, long resetEpochSecond, long retryAfterSeconds) {
    }
}
