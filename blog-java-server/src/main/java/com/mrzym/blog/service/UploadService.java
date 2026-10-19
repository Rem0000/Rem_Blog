package com.mrzym.blog.service;

import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.config.BlogProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

/**
 * 图片上传，对齐原实现 controller/utils 的 upload：
 *  - oss：上传到阿里云 OSS，返回 OSS 公网 URL（生产/默认推荐）
 *  - local：落盘到 upload/local，返回 http://127.0.0.1:${APP_PORT}/local/<文件名>（本地调试兜底）
 *  - online：落盘到 upload/online，返回 .../online/<文件名>
 *  - qiniu / minio：依赖云 SDK 与密钥，未配置时返回明确错误（TODO）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadService {

    private final BlogProperties blogProperties;
    private final OssService ossService;

    @Value("${server.port:8088}")
    private int port;

    public String upload(MultipartFile file) {
        return upload(file, null);
    }

    /**
     * @param requestReferer 本次上传请求的 Origin / Referer，OSS 模式下会带给 OSS
     */
    public String upload(MultipartFile file, String requestReferer) {
        String mode = blogProperties.getUploadType();
        if (mode == null || mode.isBlank()) {
            throw new BizException(ErrorCode.UPLOAD, "请配置文件上传模式");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.UPLOAD, "文件上传失败");
        }

        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "oss" -> ossService.upload(file, requestReferer);
            case "local", "online" -> uploadToLocal(file, mode.toLowerCase(Locale.ROOT));
            default -> throw new BizException(ErrorCode.UPLOAD, "当前上传模式未配置：" + mode);
        };
    }

    /**
     * 本地落盘模式，保留给本地调试/回退使用。目录按进程工作目录解析（upload/local、upload/online）。
     */
    private String uploadToLocal(MultipartFile file, String mode) {
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            String candidate = original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (candidate.matches("[a-z0-9]{1,10}")) {
                ext = "." + candidate;
            }
        }
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;

        try {
            Path dir = Paths.get("upload", mode).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(fileName).toFile());
        } catch (IOException e) {
            log.error("保存上传文件失败", e);
            throw new BizException(ErrorCode.UPLOAD, "文件上传失败");
        }
        return "http://127.0.0.1:" + port + "/" + mode + "/" + fileName;
    }
}
