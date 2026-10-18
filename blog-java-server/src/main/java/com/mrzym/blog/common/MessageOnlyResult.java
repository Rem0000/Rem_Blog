package com.mrzym.blog.common;

import lombok.Data;

/**
 * 只有 code + message 的响应，对应原实现 result("...") 只传一个参数的情况：
 * JSON.stringify 会把 undefined 的 result 直接丢掉，所以响应里没有 result 字段。
 */
@Data
public class MessageOnlyResult {

    private Object code = 0;

    private String message;

    public static MessageOnlyResult of(String message) {
        MessageOnlyResult r = new MessageOnlyResult();
        r.message = message;
        return r;
    }
}
