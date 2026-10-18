package com.mrzym.blog.common;

import lombok.Data;

/**
 * 错误响应体：{"code":"100016","message":"..."}
 *
 * 单独一个类，是为了不出现 result 字段——原实现的 throwError() 只返回
 * {code, message}，多一个 "result": null 就是契约偏差。
 */
@Data
public class ErrorResult {

    private final String code;
    private final String message;
}
