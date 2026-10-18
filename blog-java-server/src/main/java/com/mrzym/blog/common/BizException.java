package com.mrzym.blog.common;

import lombok.Getter;

/**
 * 业务异常：携带原实现的错误码，由全局异常处理器映射成 HTTP 状态。
 */
@Getter
public class BizException extends RuntimeException {

    private final String code;

    public BizException(String code, String message) {
        super(message);
        this.code = code;
    }
}
