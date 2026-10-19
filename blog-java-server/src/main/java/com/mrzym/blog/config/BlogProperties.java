package com.mrzym.blog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "blog")
public class BlogProperties {

    /** JWT 密钥，对应原实现 .env 的 JWT_SECRET */
    private String jwtSecret = "blog";

    /** 内置超管 admin 的密码，对应 .env 的 ADMIN_PASSWORD */
    private String adminPassword = "";

    /** 上传方式：oss / local / online（qiniu / minio 未实现） */
    private String uploadType = "oss";
}
