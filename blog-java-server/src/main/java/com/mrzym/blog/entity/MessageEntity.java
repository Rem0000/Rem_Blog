package com.mrzym.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.time.LocalDateTime;

/** 留言板（blog_message） */
@Data
@TableName("blog_message")
@JsonPropertyOrder({"createdAt", "updatedAt", "id", "message", "color", "font_size", "font_weight",
        "bg_color", "bg_url", "user_id", "nick_name", "tag", "like_times"})
public class MessageEntity {

    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String message;

    private String color;

    /** JSON: font_size */
    private Integer fontSize;

    /** JSON: font_weight */
    private Integer fontWeight;

    /** JSON: bg_color */
    private String bgColor;

    /** JSON: bg_url */
    private String bgUrl;

    /** JSON: user_id */
    private Integer userId;

    /** JSON: nick_name */
    private String nickName;

    private String tag;

    /** JSON: like_times */
    private Integer likeTimes;
}
