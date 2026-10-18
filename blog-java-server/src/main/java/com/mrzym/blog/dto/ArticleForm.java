package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 文章新增/修改请求体。
 *
 * tagList 必须显式 @JsonProperty：全局 SNAKE_CASE 对反序列化也生效，
 * 否则 Jackson 会去找 tag_list，而前端传的是 tagList。
 */
@Data
public class ArticleForm {

    private Object id;

    /** JSON: article_title */
    private String articleTitle;

    /** JSON: author_id */
    private Object authorId;

    private ArticleCategoryForm category;

    /** JSON: article_description */
    private String articleDescription;

    /** JSON: article_content */
    private String articleContent;

    /** JSON: article_cover */
    private String articleCover;

    /** JSON: is_top */
    private Object isTop;

    private Object order;

    private Object status;

    private Object type;

    /** JSON: origin_url */
    private String originUrl;

    @JsonProperty("tagList")
    private List<ArticleTagForm> tagList;
}
