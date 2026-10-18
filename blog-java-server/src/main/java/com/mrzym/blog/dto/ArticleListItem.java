package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 首页文章列表项。字段名逐个对齐线上响应：
 *
 * createdAt, updatedAt, id, article_title, author_id, category_id, article_description,
 * article_cover, is_top, order, status, type, view_times, thumbs_up_times,
 * reading_duration, categoryName, tagNameList
 *
 * 其中 createdAt / updatedAt / categoryName / tagNameList 是驼峰，其余是下划线。
 */
@Data
public class ArticleListItem {

    // 注意：只写 @JsonProperty 不够，不加 @JsonFormat 会输出成 2026-10-18T19:09:39
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

    /** JSON: article_cover */
    private String articleCover;

    /** JSON: is_top */
    private Integer isTop;

    /** JSON: order（MySQL 保留字，查询里要用反引号） */
    private Integer order;

    private Integer status;

    private Integer type;

    /** JSON: view_times */
    private Integer viewTimes;

    /** JSON: thumbs_up_times */
    private Integer thumbsUpTimes;

    /** JSON: reading_duration */
    private Double readingDuration;

    /** JSON 保持驼峰：分类名 */
    @JsonProperty("categoryName")
    private String categoryName;

    /** JSON 保持驼峰：标签名数组 */
    @JsonProperty("tagNameList")
    private List<String> tagNameList;
}
