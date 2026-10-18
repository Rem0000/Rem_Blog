package com.mrzym.blog.dto;

import lombok.Data;

/** 留言分页：{current, size, message, time, tag, user_id} */
@Data
public class MessageQuery {

    private Object current;

    private Object size;

    /** 内容模糊查询 */
    private String message;

    /** 区间数组 */
    private Object time;

    private String tag;

    /** JSON: user_id，有值时用来判断 is_like */
    private Object userId;
}
