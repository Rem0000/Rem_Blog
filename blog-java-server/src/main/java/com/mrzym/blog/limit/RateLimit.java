package com.mrzym.blog.limit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级限流，对应原实现 middleware/limit-request 的 createTimesLimiter。
 *
 * 原实现用 koa2-ratelimit（固定窗口），超限时返回：
 *   429 + {"message":"..."} + X-Ratelimit-* 头
 * 这里保持一致。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 限流桶标识，同一接口内唯一即可（对应原实现的 prefixKey） */
    String key();

    /** 窗口内最大请求数 */
    int max();

    /** 窗口长度（秒），默认 60 秒，与原实现 interval 默认值一致 */
    int windowSeconds() default 60;

    /** 超限提示，默认值照抄原实现 */
    String message() default "小黑子 压测我是吧";
}
