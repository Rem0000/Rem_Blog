package com.mrzym.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章（blog_article）。
 *
 * 字段顺序与现网响应一致：createdAt / updatedAt 在最前，然后是 id、下划线字段。
 * `order` 是 MySQL 保留字，必须用 @TableField 加反引号，否则 select/insert 直接语法错误。
 */
@Data
@TableName("blog_article")
@JsonPropertyOrder({"createdAt", "updatedAt", "id", "article_title", "author_id", "category_id",
        "article_description", "article_content", "article_cover", "is_top", "order", "status",
        "type", "origin_url", "view_times", "thumbs_up_times", "reading_duration"})
public class ArticleEntity {

    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String articleTitle;

    private Integer authorId;

    private Integer categoryId;

    private String articleDescription;

    private String articleContent;

    private String articleCover;

    private Integer isTop;

    @TableField("`order`")
    private Integer order;

    private Integer status;

    private Integer type;

    private String originUrl;

    private Integer viewTimes;

    private Integer thumbsUpTimes;

    private Double readingDuration;
}
