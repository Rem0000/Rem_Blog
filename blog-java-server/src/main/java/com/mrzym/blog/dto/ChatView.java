package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

/**
 * 聊天列表项。注意 createdAt/updatedAt 是 ISO UTC 字符串：
 * 原实现直接改的是 `message.dataValues`，Date 绕过了 model 的 getter，
 * JSON 化后就是 "2026-10-10T04:06:11.000Z"。
 */
@Data
@JsonPropertyOrder({"id", "user_id", "content", "content_type", "createdAt", "updatedAt",
        "nick_name", "avatar"})
public class ChatView {

    private Integer id;

    /** JSON: user_id */
    private Integer userId;

    private String content;

    /** JSON: content_type */
    private String contentType;

    @JsonProperty("createdAt")
    private String createdAt;

    @JsonProperty("updatedAt")
    private String updatedAt;

    /** JSON: nick_name */
    private String nickName;

    private String avatar;
}
