package com.mrzym.blog.config;

import com.mrzym.blog.auth.AuthInterceptor;
import com.mrzym.blog.limit.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 限流在前（原实现 createTimesLimiter 挂在 auth 之前），鉴权在后。
        // 两者都只对带注解的 handler 生效，其余直接放行。
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/**").order(0);
        registry.addInterceptor(authInterceptor).addPathPatterns("/**").order(1);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 开发期前端跑在 8080，接口在 8888；生产走 nginx 同源，不影响
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 上传后的静态访问，对应 Node 的 koa-static(upload)：
        //   /local/xxx  -> upload/local/xxx
        //   /online/xxx -> upload/online/xxx
        registry.addResourceHandler("/local/**").addResourceLocations(dirUri("upload", "local"));
        registry.addResourceHandler("/online/**").addResourceLocations(dirUri("upload", "online"));
    }

    /** 目录 -> file: URI，确保以 / 结尾（否则 addResourceLocations 会拼错路径） */
    private static String dirUri(String first, String second) {
        String uri = java.nio.file.Paths.get(first, second).toAbsolutePath().normalize()
                .toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }
}
