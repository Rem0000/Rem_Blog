package com.mrzym.blog.dto;

import lombok.Data;

/** 评论总数：{for_id, type} */
@Data
public class CommentTotalQuery {

    /** JSON: for_id */
    private Object forId;

    private Object type;
}
