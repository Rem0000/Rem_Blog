package com.mrzym.blog.dto;

import lombok.Data;

import java.util.List;

/**
 * 首页/时间轴等路径分页的响应：{current, size, list, total}。
 *
 * current/size 用 Object 原样回显：现网是路径参数，返回的是字符串 "1"/"2"。
 */
@Data
public class ArticleListResult {

    private Object current;

    private Object size;

    private List<?> list;

    private long total;

    public static ArticleListResult of(Object current, Object size, long total, List<?> list) {
        ArticleListResult r = new ArticleListResult();
        r.current = current;
        r.size = size;
        r.total = total;
        r.list = list;
        return r;
    }
}
