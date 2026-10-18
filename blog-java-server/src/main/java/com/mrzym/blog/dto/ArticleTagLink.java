package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.time.LocalDateTime;

/** 新增文章后返回的关联记录，对齐 Sequelize bulkCreate 的 dataValues */
@Data
@JsonPropertyOrder({"createdAt", "updatedAt", "id", "article_id", "tag_id"})
public class ArticleTagLink {

    @JsonProperty("createdAt")
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    private Integer id;

    /** JSON: article_id */
    private Integer articleId;

    /** JSON: tag_id */
    private Integer tagId;
}
