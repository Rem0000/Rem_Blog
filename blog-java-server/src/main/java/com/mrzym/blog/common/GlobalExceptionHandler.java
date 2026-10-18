package com.mrzym.blog.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理，映射规则照抄原实现 src/app/errorHandler.js：
 *   100002 -> 403
 *   100016 -> 401
 *   其它   -> 500
 *
 * 响应体用 ErrorResult（只有 code + message），对齐原实现的 throwError()，
 * 不能带 result 字段。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 未匹配到任何接口：保持 404，前端靠状态码提示"接口没有找到" */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResult> handleNotFound(NoResourceFoundException e) {
        return ResponseEntity.status(404).body(new ErrorResult("404", "接口没有找到"));
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ErrorResult> handleBiz(BizException e) {
        log.warn("业务异常 code={} message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(ErrorCode.httpStatusOf(e.getCode()))
                .body(new ErrorResult(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResult> handleOther(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(500)
                .body(new ErrorResult(ErrorCode.INTERNAL, "服务器内部错误"));
    }
}
