package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章详情。在实体字段之外追加：
 * tagIdList / tagNameList / authorName / categoryName（都是驼峰，前端在读写）。
 */
@Data
@JsonPropertyOrder({"createdAt", "updatedAt", "id", "article_title", "author_id", "category_id",
        "article_description", "article_content", "article_cover", "is_top", "order", "status",
        "type", "origin_url", "view_times", "thumbs_up_times", "reading_duration",
        "tagIdList", "tagNameList", "authorName", "categoryName"})
public class ArticleDetail {

    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    private Integer id;

    /** JSON: article_title */
    private String articleTitle;

    /** JSON: author_id */
    private Integer authorId;

    /** JSON: category_id */
    private Integer categoryId;

    /** JSON: article_description */
    private String articleDescription;

    /** JSON: article_content */
    private String articleContent;

    /** JSON: article_cover */
    private String articleCover;

    /** JSON: is_top */
    private Integer isTop;

    private Integer order;

    private Integer status;

    private Integer type;

    /** JSON: origin_url */
    private String originUrl;

    /** JSON: view_times */
    private Integer viewTimes;

    /** JSON: thumbs_up_times */
    private Integer thumbsUpTimes;

    /** JSON: reading_duration */
    private Double readingDuration;

    @JsonProperty("tagIdList")
    private List<Integer> tagIdList;

    @JsonProperty("tagNameList")
    private List<String> tagNameList;

    @JsonProperty("authorName")
    private String authorName;

    @JsonProperty("categoryName")
    private String categoryName;
}
