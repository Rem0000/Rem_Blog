package com.mrzym.blog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 配置（UPLOADTYPE=oss 时生效）。
 *
 * <p>推荐用环境变量注入密钥：
 * <pre>
 * ALIBABA_CLOUD_ACCESS_KEY_ID=...
 * ALIBABA_CLOUD_ACCESS_KEY_SECRET=...
 * OSS_ENDPOINT=oss-cn-shanghai.aliyuncs.com
 * OSS_BUCKET_NAME=your-bucket
 * OSS_DOMAIN=img.example.com   # 可选，自定义域名 / CDN
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "oss")
public class OssProperties {

    /** 地域节点，如 oss-cn-shanghai.aliyuncs.com（可带 http(s)://） */
    private String endpoint;

    /** AccessKey ID */
    private String accessKeyId;

    /** AccessKey Secret */
    private String accessKeySecret;

    /** Bucket 名称 */
    private String bucketName;

    /**
     * 自定义访问域名 / CDN 域名，可带协议。
     * 留空时使用 {@code https://<bucket>.<endpoint>} 默认域名。
     */
    private String domain;

    /** 对象前缀，默认 images/（建议保留，方便和 Bucket 里其他文件区分） */
    private String dir = "images/";

    /**
     * 防盗链用的 Referer。
     *
     * <p>填 OSS 白名单里的站点地址，例如 {@code https://mrzym.top/}。
     * 留空时上传接口会自动使用本次上传请求的 Origin / Referer。
     */
    private String referer;

    /**
     * 上传时是否把对象 ACL 设为公共读。
     * 博客图片需要浏览器直接访问，默认开启；如果 Bucket 开启了「阻止公共访问」，
     * 需要先在 OSS 控制台关闭它（或把 Bucket 读权限改成公共读）。
     */
    private boolean publicRead = true;
}
