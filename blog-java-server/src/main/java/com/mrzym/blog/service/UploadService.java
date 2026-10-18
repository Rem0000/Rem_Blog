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
import java.util.UUID;

/**
 * 图片上传，对齐原实现 controller/utils 的 upload：
 *  - local：落盘到 upload/local，返回 http://127.0.0.1:${APP_PORT}/local/<文件名>
 *  - online：落盘到 upload/online，返回 .../online/<文件名>（原实现没返回 body，这里补成可用）
 *  - qiniu / minio：依赖云 SDK 与密钥，未配置时返回明确错误（TODO）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadService {

    private final BlogProperties blogProperties;

    @Value("${server.port:8088}")
    private int port;

    public String upload(MultipartFile file) {
        String mode = blogProperties.getUploadType();
        if (mode == null || mode.isBlank()) {
            throw new BizException(ErrorCode.UPLOAD, "请配置文件上传模式");
        }
        if (!"local".equals(mode) && !"online".equals(mode)) {
            // qiniu / minio 需要 ACCESSKEY/SECRETKEY/BUCKET，迁移期先明确报错
            throw new BizException(ErrorCode.UPLOAD, "当前上传模式未配置：" + mode);
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.UPLOAD, "文件上传失败");
        }

        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
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
