package com.mrzym.blog.common;

import lombok.Data;

/**
 * 提示型响应：{"code":100,"message":"..."}，没有 result 字段。
 * 对应 Node 里的 tipsResult()。
 */
@Data
public class Tips {

    private final int code = 100;
    private final String message;

    private Tips(String message) {
        this.message = message;
    }

    public static Tips of(String message) {
        return new Tips(message);
    }
}
