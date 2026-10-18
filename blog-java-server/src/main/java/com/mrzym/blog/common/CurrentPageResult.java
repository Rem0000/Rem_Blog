package com.mrzym.blog.common;

import lombok.Data;

import java.util.List;

/**
 * 现网 body 分页形状：{current, size, list, total}。
 *
 * 说明：README 里写的 page/pageSize 是迁移初期的约定，但实际现网
 * （mrzym.top）以及前端 link-list.vue / links/index.ts 用的都是
 * current/size，这里以现网 + 前端为准。字段顺序也照现网：list 在 total 前。
 */
@Data
public class CurrentPageResult<T> {

    private Object current;

    private Object size;

    private List<T> list;

    private long total;

    public static <T> CurrentPageResult<T> of(Object current, Object size, long total, List<T> list) {
        CurrentPageResult<T> r = new CurrentPageResult<>();
        r.current = current;
        r.size = size;
        r.list = list;
        r.total = total;
        return r;
    }
}
