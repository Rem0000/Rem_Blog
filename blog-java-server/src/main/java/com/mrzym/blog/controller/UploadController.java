package com.mrzym.blog.controller;

import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.Result;
import com.mrzym.blog.limit.RateLimit;
import com.mrzym.blog.service.UploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 上传，对齐原实现 router/utils.js：POST /upload/img（字段名 file），1 分钟 100 次。
 */
@Slf4j
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @PostMapping("/img")
    @RateLimit(key = "upload:img", max = 100, windowSeconds = 60, message = "上传图片过于频繁 请稍后再试")
    public Result<Map<String, Object>> upload(
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestHeader(value = "Origin", required = false) String origin,
            @RequestHeader(value = "Referer", required = false) String referer) {
        try {
            String url = uploadService.upload(file, chooseReferer(origin, referer));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("url", url);
            return Result.ok("图片上传成功", data);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new BizException(com.mrzym.blog.common.ErrorCode.UPLOAD, "文件上传失败");
        }
    }

    /**
     * 把上传请求的来源转成 OSS 防盗链用的 Referer：
     * 优先 Origin，其次从 Referer 里截出 scheme + host + port，避免把后台页面路径带给 OSS。
     * 取不到时返回 null，交给 oss.referer 配置兜底。
     */
    static String chooseReferer(String origin, String referer) {
        String value = StringUtils.hasText(origin) ? origin : referer;
        if (!StringUtils.hasText(value) || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        try {
            URI uri = URI.create(value.trim());
            if (uri.getScheme() != null && uri.getHost() != null) {
                String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
                String host = uri.getHost().toLowerCase(Locale.ROOT);
                StringBuilder sb = new StringBuilder(scheme).append("://").append(host);
                int port = uri.getPort();
                boolean defaultPort = ("http".equals(scheme) && port == 80)
                        || ("https".equals(scheme) && port == 443);
                if (port > 0 && !defaultPort) {
                    sb.append(':').append(port);
                }
                return sb.toString();
            }
        } catch (IllegalArgumentException ignored) {
            // 不是标准 URL 时原样传给 OSS，让白名单自己去匹配
        }
        return value.trim();
    }
}
