package com.mrzym.blog.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 访问级别，对应原实现 middleware/auth 里的四个中间件。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    Level value() default Level.LOGIN;

    enum Level {
        /** 仅要求已登录（auth） */
        LOGIN,
        /** 要求 role == 1（needAdminAuth） */
        ADMIN,
        /** 要求 role == 1 且 username != admin（needAdminAuthNotNeedSuper） */
        ADMIN_NOT_SUPER,
        /** 要求 username != admin（isSuperAdmin） */
        NOT_SUPER
    }
}
