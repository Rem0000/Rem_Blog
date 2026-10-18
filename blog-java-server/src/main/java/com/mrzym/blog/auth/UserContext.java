package com.mrzym.blog.auth;

import java.util.Map;

/**
 * 当前请求的登录用户（来自 JWT payload）。
 * 原实现是 ctx.state.user，这里用 ThreadLocal 等价替代。
 */
public final class UserContext {

    private static final ThreadLocal<Map<String, Object>> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Map<String, Object> claims) {
        HOLDER.set(claims);
    }

    public static Map<String, Object> get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static String username() {
        Map<String, Object> claims = HOLDER.get();
        if (claims == null || claims.get("username") == null) {
            return null;
        }
        return String.valueOf(claims.get("username"));
    }

    public static Long id() {
        Map<String, Object> claims = HOLDER.get();
        if (claims == null || claims.get("id") == null) {
            return null;
        }
        return Long.valueOf(String.valueOf(claims.get("id")));
    }
}
