package com.mrzym.blog.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 工具，对齐原实现 utils/tool.js 的用法：
 *   ip = X-Real-IP || X-Forwarded-For || ctx.ip;  然后去掉端口。
 *
 * <p>IP -> 属地的解析已迁到 {@link com.mrzym.blog.service.IpLocationService}（ip2region 离线库）。
 */
public final class IpUtil {

    private IpUtil() {
    }

    public static String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Real-IP");
        if (isBlank(ip)) {
            ip = request.getHeader("X-Forwarded-For");
        }
        if (isBlank(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 去掉 IP 后面的端口：
     *  - 127.0.0.1:8080      -> 127.0.0.1
     *  - [::1]:8080          -> ::1
     *  - ::1 / 2001:db8::1   -> 原样返回（IPv6 本身带多个冒号）
     */
    public static String stripPort(String ip) {
        if (ip == null) {
            return null;
        }
        String value = ip.trim();
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end > 0) {
                return value.substring(1, end);
            }
        }
        int first = value.indexOf(':');
        int last = value.lastIndexOf(':');
        if (first >= 0 && first == last) {
            return value.substring(0, first);
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
