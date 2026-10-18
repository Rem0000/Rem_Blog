package com.mrzym.blog.dto;

import lombok.Data;

/** 新增聊天：{content_type, content, user_id} */
@Data
public class ChatForm {

    /** JSON: content_type */
    private String contentType;

    private String content;

    /** JSON: user_id */
    private Object userId;
}
