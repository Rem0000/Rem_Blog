package com.mrzym.blog.auth;

/**
 * token 解析失败。expired 用来区分"过期"和"无效"，
 * 因为原实现对这两种情况返回不同的错误码（100016 过期 / 100002 无效）。
 */
public class TokenException extends RuntimeException {

    private final boolean expired;

    public TokenException(String message, boolean expired) {
        super(message);
        this.expired = expired;
    }

    public boolean isExpired() {
        return expired;
    }
}
