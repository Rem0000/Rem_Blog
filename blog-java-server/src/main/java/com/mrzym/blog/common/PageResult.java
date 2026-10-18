package com.mrzym.blog.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 分页响应：{page, pageSize, total, list}
 *
 * 说明：
 *  - 入参统一为 page / pageSize（1 基）；
 *  - page 在全局 SNAKE_CASE 下正好是 page，pageSize 会被转成 page_size，
 *    所以显式覆盖回驼峰；
 *  - page/pageSize 用 Object 原样回显，前端传字符串也能原样拿回。
 */
@Data
public class PageResult<T> {

    private Object page;

    @JsonProperty("pageSize")
    private Object pageSize;

    private long total;

    private List<T> list;

    public static <T> PageResult<T> of(Object page, Object pageSize, long total, List<T> list) {
        PageResult<T> p = new PageResult<>();
        p.page = page;
        p.pageSize = pageSize;
        p.total = total;
        p.list = list;
        return p;
    }
}
