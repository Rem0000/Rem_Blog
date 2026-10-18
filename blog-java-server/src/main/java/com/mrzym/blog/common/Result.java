package com.mrzym.blog.common;

import lombok.Data;

/**
 * 统一成功/错误响应体。
 *
 * 注意 code 的类型是混的：
 *  - 成功：数字 0
 *  - 错误：字符串，如 "100016"（原实现 ERRORCODE 就是字符串，前端用 code + "" 比较）
 * 所以这里用 Object 承载，不能写成 int。
 */
@Data
public class Result<T> {

    private Object code;
    private String message;
    private T result;

    public static <T> Result<T> ok(String message, T result) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.message = message;
        r.result = result;
        return r;
    }

    public static <T> Result<T> ok(String message) {
        return ok(message, null);
    }

    public static <T> Result<T> error(String code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
