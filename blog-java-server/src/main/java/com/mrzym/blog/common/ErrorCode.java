package com.mrzym.blog.common;

/**
 * 错误码。与原实现 src/result/index.js 一一对应，值是字符串。
 */
public final class ErrorCode {

    private ErrorCode() {
    }

    public static final String USER = "100001";
    /** 权限不足 -> HTTP 403 */
    public static final String AUTH = "100002";
    public static final String TAG = "100003";
    public static final String CATEGORY = "100004";
    public static final String ARTICLE = "100005";
    public static final String UPLOAD = "100006";
    public static final String CONFIG = "100007";
    public static final String STATISTIC = "100008";
    public static final String PHOTO_ALBUM = "100009";
    public static final String PHOTO = "100010";
    public static final String TALK = "100011";
    public static final String MESSAGE = "100012";
    /** 原实现里 RECOMMEND 和 MESSAGE 复用同一个值，保持一致 */
    public static final String RECOMMEND = "100012";
    public static final String HEADER = "100013";
    public static final String LINKS = "100014";
    public static final String COMMENT = "100015";
    /** 登录过期 -> HTTP 401（前端靠它清 token） */
    public static final String AUTH_TOKEN = "100016";
    public static final String NOTIFY = "100017";
    public static final String LIKE = "100018";
    public static final String CHAT = "100019";
    public static final String TIPS = "111111";
    /** 兜底：未预期异常 */
    public static final String INTERNAL = "500000";

    /**
     * 错误码 -> HTTP 状态码，照抄 errorHandler.js
     */
    public static int httpStatusOf(String code) {
        if (AUTH.equals(code)) {
            return 403;
        }
        if (AUTH_TOKEN.equals(code)) {
            return 401;
        }
        return 500;
    }
}
