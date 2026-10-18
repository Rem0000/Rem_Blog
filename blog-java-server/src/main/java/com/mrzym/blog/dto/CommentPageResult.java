package com.mrzym.blog.dto;

import lombok.Data;

import java.util.List;

/** 评论分页响应：字段顺序照现网 {current, size, total, list} */
@Data
public class CommentPageResult<T> {

    private Object current;

    private Object size;

    private long total;

    private List<T> list;

    public static <T> CommentPageResult<T> of(Object current, Object size, long total, List<T> list) {
        CommentPageResult<T> r = new CommentPageResult<>();
        r.current = current;
        r.size = size;
        r.total = total;
        r.list = list;
        return r;
    }
}
