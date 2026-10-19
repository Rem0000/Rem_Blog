package com.mrzym.blog.service;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.config.BlogProperties;
import com.mrzym.blog.config.OssProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 阿里云 OSS 图片存储。
 *
 * <p>图片最终地址：
 * <ul>
 *   <li>配置了 {@code oss.domain}：{@code https://<domain>/<dir>/<uuid>.<ext>}</li>
 *   <li>没配：{@code https://<bucket>.<endpoint>/<dir>/<uuid>.<ext>}</li>
 * </ul>
 *
 * <p>OSS 客户端懒加载：只有真正上传时才创建，local/online 模式下不会连接 OSS。
 */
@Slf4j
@Service
public class OssService {

    private static final Pattern SAFE_EXTENSION = Pattern.compile("[a-z0-9]{1,10}");

    private final OssProperties properties;
    private final BlogProperties blogProperties;

    /** OSS 客户端线程安全，整个应用共用一个即可 */
    private volatile OSS client;

    @Autowired
    public OssService(OssProperties properties, BlogProperties blogProperties) {
        this.properties = properties;
        this.blogProperties = blogProperties;
    }

    /** 测试用：不注入客户端，只验证配置校验/URL 解析 */
    OssService(OssProperties properties) {
        this(properties, new BlogProperties());
    }

    /** 测试用：注入已经构建好的 OSS 客户端，避免真的连接 OSS */
    OssService(OssProperties properties, OSS client) {
        this(properties, new BlogProperties());
        this.client = client;
    }

    /**
     * 启动时打印 OSS 配置是否读到，方便排查“系统环境变量已设置但进程没继承”的问题。
     * 只打印是否设置和 AccessKeyId 的前几位，不会输出 Secret。
     */
    @PostConstruct
    public void logConfiguration() {
        boolean keyLoaded = StringUtils.hasText(properties.getAccessKeyId());
        boolean secretLoaded = StringUtils.hasText(properties.getAccessKeySecret());
        if (keyLoaded && secretLoaded) {
            String key = properties.getAccessKeyId().trim();
            String masked = key.length() > 6 ? key.substring(0, 4) + "***" + key.substring(key.length() - 2) : "***";
            log.info("OSS 配置已加载: endpoint={}, bucket={}, accessKeyId={}",
                    properties.getEndpoint(), properties.getBucketName(), masked);
        } else {
            log.warn("OSS 配置未完整加载: endpoint={}, bucket={}, accessKeyId={}, accessKeySecret={}。"
                            + "如果使用 oss 上传模式，请确认系统环境变量在启动进程之前已生效"
                            + "（IDE / 终端需要完全退出后重开，而不是只重启项目）",
                    properties.getEndpoint(), properties.getBucketName(),
                    keyLoaded ? "已设置" : "缺失", secretLoaded ? "已设置" : "缺失");
        }
    }

    /**
     * 上传图片到 OSS，返回可公开访问的 URL。
     */
    public String upload(MultipartFile file) {
        return upload(file, null);
    }

    /**
     * @param requestReferer 本次上传请求的 Origin / Referer；oss.referer 为空时用它兜底
     */
    public String upload(MultipartFile file, String requestReferer) {
        ensureConfigured();

        String objectKey = buildObjectKey(file);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(detectContentType(file, objectKey));
        // 文件名是 UUID，内容不会变，可以放心长缓存
        metadata.setCacheControl("public, max-age=31536000");
        if (properties.isPublicRead()) {
            // Bucket 默认私有；博客图片需要浏览器直链访问，所以对象单独设成公共读
            metadata.setObjectAcl(CannedAccessControlList.PublicRead);
        }

        long start = System.currentTimeMillis();
        try (InputStream in = file.getInputStream()) {
            PutObjectRequest request = new PutObjectRequest(
                    properties.getBucketName().trim(), objectKey, in, metadata);
            applyReferer(request, requestReferer);
            client().putObject(request);
        } catch (OSSException e) {
            log.error("OSS 上传失败: bucket={}, key={}, code={}, requestId={}",
                    properties.getBucketName(), objectKey, e.getErrorCode(), e.getRequestId(), e);
            throw new BizException(ErrorCode.UPLOAD, "图片上传到 OSS 失败：" + friendlyOssError(e));
        } catch (ClientException e) {
            log.error("OSS 客户端错误: endpoint={}, key={}", properties.getEndpoint(), objectKey, e);
            throw new BizException(ErrorCode.UPLOAD, "连接 OSS 失败，请检查 oss.endpoint、网络和 AccessKey 配置");
        } catch (IOException e) {
            log.error("读取上传文件失败", e);
            throw new BizException(ErrorCode.UPLOAD, "文件上传失败");
        }

        String url = publicUrl(objectKey);
        log.info("OSS 上传成功: key={}, size={}B, {}ms", objectKey, file.getSize(),
                System.currentTimeMillis() - start);
        return url;
    }

    /**
     * 防盗链：给发往 OSS 的 SDK 请求补上 Referer。
     * 优先使用配置的 oss.referer，未配置时用上传请求自身的 Origin / Referer。
     */
    private void applyReferer(PutObjectRequest request, String requestReferer) {
        String referer = StringUtils.hasText(properties.getReferer())
                ? properties.getReferer().trim() : requestReferer;
        if (StringUtils.hasText(referer)) {
            request.addHeader("Referer", referer);
        }
    }

    // ==================== 删除旧图 ====================

    /** 批量删除旧图，返回成功删除的数量；外部 URL / 非图片目录的 URL 会自动跳过 */
    public int deleteByUrls(Collection<String> urls) {
        if (!ossMode() || urls == null || urls.isEmpty()) {
            return 0;
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String url : urls) {
            String key = toObjectKey(url);
            if (key != null) {
                keys.add(key);
            }
        }
        int deleted = 0;
        for (String key : keys) {
            if (deleteKey(key)) {
                deleted++;
            }
        }
        return deleted;
    }

    /**
     * 删除一个 OSS 旧图，best-effort，不抛异常：
     *  - 只处理当前 Bucket / oss.domain 下的 URL；
     *  - 只处理 oss.dir 前缀下的对象，避免误删 Bucket 里其他文件；
     *  - 本地 / 外部 / 历史 URL 直接跳过。
     */
    public boolean deleteByUrl(String url) {
        if (!ossMode()) {
            return false;
        }
        String objectKey = toObjectKey(url);
        return objectKey != null && deleteKey(objectKey);
    }

    /** 只有当前上传模式是 oss 时才清理 OSS；local/online 调试时不动远端 */
    private boolean ossMode() {
        return blogProperties != null && "oss".equalsIgnoreCase(blogProperties.getUploadType());
    }

    private boolean deleteKey(String objectKey) {
        if (!isConfigured()) {
            return false;
        }
        try {
            client().deleteObject(properties.getBucketName().trim(), objectKey);
            log.info("OSS 删除旧图成功: key={}", objectKey);
            return true;
        } catch (OSSException e) {
            log.warn("OSS 删除旧图失败: key={}, code={}, message={}",
                    objectKey, e.getErrorCode(), e.getErrorMessage());
        } catch (ClientException e) {
            log.warn("OSS 删除旧图失败: key={}, endpoint={}", objectKey, properties.getEndpoint(), e);
        } catch (Exception e) {
            log.warn("OSS 删除旧图失败: key={}", objectKey, e);
        }
        return false;
    }

    /**
     * 从公开 URL 反推对象 key，只接受当前 Bucket / 自定义域名，且必须位于 oss.dir 下。
     * 返回 null 表示这个 URL 不该由当前 OSS 配置删除。
     */
    private String toObjectKey(String url) {
        if (!isConfigured() || !StringUtils.hasText(url)) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            return null;
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean customDomain = isCustomDomainHost(host);
        if (!customDomain && !host.equals(defaultDomainHost())) {
            return null;
        }

        String rawPath = uri.getRawPath();
        if (!StringUtils.hasText(rawPath)) {
            return null;
        }
        String path;
        try {
            path = UriUtils.decode(rawPath, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (customDomain) {
            String basePath = customDomainBasePath();
            if (StringUtils.hasText(basePath)) {
                if (!path.equals(basePath) && !path.startsWith(basePath + "/")) {
                    return null;
                }
                path = path.substring(basePath.length());
            }
        }

        path = path.replaceFirst("^/+", "");
        String prefix = objectPrefix();
        if (!StringUtils.hasText(path) || (StringUtils.hasText(prefix) && !path.startsWith(prefix))) {
            return null;
        }
        return path;
    }

    private boolean isCustomDomainHost(String host) {
        String customHost = customDomainHost();
        return customHost != null && host.equals(customHost);
    }

    private String customDomainHost() {
        if (!StringUtils.hasText(properties.getDomain())) {
            return null;
        }
        try {
            String host = URI.create(normalizedDomain()).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String customDomainBasePath() {
        try {
            String path = URI.create(normalizedDomain()).getPath();
            if (!StringUtils.hasText(path) || "/".equals(path)) {
                return "";
            }
            path = path.replaceAll("/+$", "");
            return path.startsWith("/") ? path : "/" + path;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private String defaultDomainHost() {
        return (properties.getBucketName().trim() + "." + endpointHost()).toLowerCase(Locale.ROOT);
    }

    private String normalizedDomain() {
        String domain = trimTrailingSlash(properties.getDomain().trim());
        return hasScheme(domain) ? domain : "https://" + domain;
    }

    @PreDestroy
    public void shutdown() {
        OSS current = client;
        if (current != null) {
            current.shutdown();
        }
    }

    // ==================== 配置 ====================

    private void ensureConfigured() {
        List<String> missing = missingConfig();
        if (!missing.isEmpty()) {
            throw new BizException(ErrorCode.UPLOAD,
                    "OSS 未配置：" + String.join("、", missing)
                            + "，请填写 application-local.yml 或设置对应的环境变量");
        }
    }

    private boolean isConfigured() {
        return missingConfig().isEmpty();
    }

    private List<String> missingConfig() {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(properties.getEndpoint())) {
            missing.add("oss.endpoint");
        }
        if (!StringUtils.hasText(properties.getAccessKeyId())) {
            missing.add("oss.access-key-id");
        }
        if (!StringUtils.hasText(properties.getAccessKeySecret())) {
            missing.add("oss.access-key-secret");
        }
        if (!StringUtils.hasText(properties.getBucketName())) {
            missing.add("oss.bucket-name");
        }
        return missing;
    }

    private OSS client() {
        OSS current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    try {
                        // 单张图最大 20MB：连接快速失败，传输给足时间
                        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
                        configuration.setConnectionTimeout(10_000);
                        configuration.setSocketTimeout(120_000);
                        configuration.setMaxConnections(50);
                        // 配了固定 Referer 时，所有 OSS SDK 请求都默认带上它
                        if (StringUtils.hasText(properties.getReferer())) {
                            configuration.addDefaultHeader("Referer", properties.getReferer().trim());
                        }
                        current = new OSSClientBuilder().build(
                                endpointWithScheme(),
                                properties.getAccessKeyId().trim(),
                                properties.getAccessKeySecret().trim(),
                                configuration);
                    } catch (RuntimeException e) {
                        log.error("初始化 OSS 客户端失败: endpoint={}", properties.getEndpoint(), e);
                        throw new BizException(ErrorCode.UPLOAD, "初始化 OSS 客户端失败，请检查 oss.endpoint");
                    }
                    client = current;
                }
            }
        }
        return current;
    }

    // ==================== 对象 Key / URL ====================

    private String buildObjectKey(MultipartFile file) {
        String extension = extractExtension(file.getOriginalFilename(), file.getContentType());
        return objectPrefix() + UUID.randomUUID().toString().replace("-", "") + extension;
    }

    /** oss.dir 归一化：去掉反斜杠/重复斜杠/开头斜杠，并保证以 / 结尾 */
    private String objectPrefix() {
        String prefix = StringUtils.hasText(properties.getDir()) ? properties.getDir() : "images/";
        prefix = prefix.replace('\\', '/').replaceAll("/{2,}", "/");
        prefix = prefix.replaceAll("^/+", "");
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        return prefix;
    }

    private String publicUrl(String objectKey) {
        String encodedKey = UriUtils.encodePath(objectKey, StandardCharsets.UTF_8);
        String domain = properties.getDomain();
        String base;
        if (StringUtils.hasText(domain)) {
            base = trimTrailingSlash(domain.trim());
            if (!hasScheme(base)) {
                base = "https://" + base;
            }
        } else {
            String scheme = endpointWithScheme().startsWith("http://") ? "http" : "https";
            base = scheme + "://" + properties.getBucketName().trim() + "." + endpointHost();
        }
        return base + "/" + encodedKey;
    }

    /** 保留配置里的协议；没写协议时默认 https */
    private String endpointWithScheme() {
        String endpoint = trimTrailingSlash(properties.getEndpoint().trim());
        return hasScheme(endpoint) ? endpoint : "https://" + endpoint;
    }

    private String endpointHost() {
        return endpointWithScheme().replaceFirst("(?i)^https?://", "");
    }

    private static boolean hasScheme(String value) {
        return value.regionMatches(true, 0, "http://", 0, 7)
                || value.regionMatches(true, 0, "https://", 0, 8);
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    // ==================== 文件类型 ====================

    private static String extractExtension(String originalFilename, String contentType) {
        if (originalFilename != null) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot >= 0 && dot < originalFilename.length() - 1) {
                String extension = originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (SAFE_EXTENSION.matcher(extension).matches()) {
                    return "." + extension;
                }
            }
        }
        return switch (contentType == null ? "" : contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/svg+xml" -> ".svg";
            case "image/bmp" -> ".bmp";
            case "image/x-icon", "image/vnd.microsoft.icon" -> ".ico";
            case "image/avif" -> ".avif";
            default -> "";
        };
    }

    private static String detectContentType(MultipartFile file, String objectKey) {
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType)
                && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            return contentType;
        }
        int dot = objectKey.lastIndexOf('.');
        String extension = dot >= 0 ? objectKey.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "bmp" -> "image/bmp";
            case "ico" -> "image/x-icon";
            case "avif" -> "image/avif";
            default -> "application/octet-stream";
        };
    }

    private String friendlyOssError(OSSException e) {
        String code = e.getErrorCode();
        if ("AccessDenied".equals(code)) {
            return "AccessDenied，请检查 AccessKey 是否有该 Bucket 的写权限，"
                    + "并在 OSS 控制台关闭「阻止公共访问」（博客图片需要公共读）";
        }
        if ("NoSuchBucket".equals(code)) {
            return "Bucket 不存在：" + properties.getBucketName();
        }
        if ("InvalidAccessKeyId".equals(code)) {
            return "AccessKeyId 无效";
        }
        if ("SignatureDoesNotMatch".equals(code)) {
            return "AccessKeySecret 不正确";
        }
        return code + (StringUtils.hasText(e.getErrorMessage()) ? "：" + e.getErrorMessage() : "");
    }
}
