package com.mrzym.blog.dto;

import lombok.Data;

/** 后台评论分页：{current, size, content, to_name, from_name, time} */
@Data
public class CommentBackQuery {

    private Object current;

    private Object size;

    private String content;

    /** JSON: to_name */
    private String toName;

    /** JSON: from_name */
    private String fromName;

    /** 区间数组 */
    private Object time;
}
