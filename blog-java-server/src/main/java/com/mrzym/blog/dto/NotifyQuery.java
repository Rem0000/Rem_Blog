package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 通知分页：{current, size, userId}（注意是驼峰 userId） */
@Data
public class NotifyQuery {

    private Object current;

    private Object size;

    @JsonProperty("userId")
    private Object userId;
}
