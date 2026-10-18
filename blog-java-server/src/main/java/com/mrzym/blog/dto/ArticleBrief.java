package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章简略信息（时间轴 / 按标签 / 按分类 / 推荐）。
 * 现网字段顺序：createdAt, id, article_title, article_cover
 */
@Data
@JsonPropertyOrder({"createdAt", "id", "article_title", "article_cover"})
public class ArticleBrief {

    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    private Integer id;

    /** JSON: article_title */
    private String articleTitle;

    /** JSON: article_cover */
    private String articleCover;
}
