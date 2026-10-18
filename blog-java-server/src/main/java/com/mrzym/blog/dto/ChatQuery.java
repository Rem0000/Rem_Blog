package com.mrzym.blog.dto;

import lombok.Data;

/** 聊天分页：{size, last_id}；last_id 用于向上翻页 */
@Data
public class ChatQuery {

    private Object size;

    /** JSON: last_id */
    private Object lastId;
}
