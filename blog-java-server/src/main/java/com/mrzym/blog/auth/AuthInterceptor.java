package com.mrzym.blog.auth;

import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * 鉴权拦截器，行为对齐原实现 middleware/auth/index.js。
 *
 * 关键：Authorization 头可能是裸 token，也可能带 "Bearer " 前缀，两种都要认
 * （原实现是 authorization.replace("Bearer ", "")，天然兼容两种）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireRole require = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (require == null) {
            require = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (require == null) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            throw new BizException(ErrorCode.AUTH_TOKEN, "您没有权限访问，请先登录");
        }

        String token = authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length())
                : authorization;

        Map<String, Object> claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (TokenException e) {
            if (e.isExpired()) {
                log.warn("token 已过期");
                throw new BizException(ErrorCode.AUTH_TOKEN, "token已过期");
            }
            log.warn("无效的 token: {}", e.getMessage());
            throw new BizException(ErrorCode.AUTH, "无效的token");
        }

        UserContext.set(claims);

        String role = claims.get("role") == null ? "" : String.valueOf(claims.get("role"));
        String username = claims.get("username") == null ? "" : String.valueOf(claims.get("username"));

        switch (require.value()) {
            case LOGIN -> {
                // 已解析即通过
            }
            case ADMIN -> {
                if (!"1".equals(role)) {
                    throw new BizException(ErrorCode.AUTH, "普通用户仅限查看");
                }
            }
            case ADMIN_NOT_SUPER -> {
                if (!"1".equals(role)) {
                    throw new BizException(ErrorCode.AUTH, "普通用户仅限查看");
                }
                if ("admin".equals(username)) {
                    throw new BizException(ErrorCode.AUTH,
                            "admin是配置的用户，没有用户信息，建议注册账号再发布博客内容");
                }
            }
            case NOT_SUPER -> {
                if ("admin".equals(username)) {
                    throw new BizException(ErrorCode.AUTH, "管理员信息只可通过配置信息修改");
                }
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        UserContext.clear();
    }
}
