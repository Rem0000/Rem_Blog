package com.mrzym.blog.controller;

import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.Result;
import com.mrzym.blog.limit.RateLimit;
import com.mrzym.blog.service.UploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
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
    public Result<Map<String, Object>> upload(@RequestPart(value = "file", required = false) MultipartFile file) {
        try {
            String url = uploadService.upload(file);
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
}
