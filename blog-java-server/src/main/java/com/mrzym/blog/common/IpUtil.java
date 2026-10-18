package com.mrzym.blog.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP / 属地工具，对齐原实现 utils/tool.js 的用法：
 *   ip = X-Real-IP || X-Forwarded-For || ctx.ip;  然后 ip.split(":").pop()
 *
 * 已知缺口：Node 用 xz-ipnet 的离线库把 IP 转成"省/国家"，Java 侧暂未接属地库，
 * 统一返回「本机地址」（与 Node 在查不到归属时的兜底一致）。接库后只需改 locate()。
 */
public final class IpUtil {

    private static final String LOCAL = "本机地址";

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

    /** 等价于 JS 的 ip.split(":").pop()：IPv4 原样，IPv6 取最后一段 */
    public static String stripPort(String ip) {
        if (ip == null) {
            return null;
        }
        int index = ip.lastIndexOf(':');
        return index >= 0 ? ip.substring(index + 1) : ip;
    }

    /**
     * IP -> 属地。迁移期无离线库：
     *  - 传进来是 IP（含点分数字）时按 Node 的兜底值返回「本机地址」；
     *  - 传进来已经是属地文本（历史数据直接存了「四川」这种）时原样返回。
     * 接上 ip2region/xz-ipnet 后只需改这里。
     */
    public static String locate(String ip) {
        if (ip == null || ip.isBlank()) {
            return LOCAL;
        }
        return ip.matches("^[0-9.]+$") || ip.contains(":") ? LOCAL : ip;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
