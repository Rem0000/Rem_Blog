package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 留言出参：实体字段 + avatar（追加）+ is_like / comment_total（仅分页列表有）。
 * is_like / comment_total 在 getAllMessage 里不存在，标 NON_NULL 让字段消失。
 */
@Data
@JsonPropertyOrder({"createdAt", "updatedAt", "id", "message", "color", "font_size", "font_weight",
        "bg_color", "bg_url", "user_id", "nick_name", "tag", "like_times",
        "avatar", "is_like", "comment_total"})
public class MessageView {

    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String avatar;

    @JsonProperty("is_like")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean isLike;

    @JsonProperty("comment_total")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long commentTotal;
}
