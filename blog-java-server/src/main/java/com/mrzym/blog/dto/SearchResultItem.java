package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

/** 全文搜索结果项：{id, article_title, article_content}（截取命中片段） */
@Data
@JsonPropertyOrder({"id", "article_title", "article_content"})
public class SearchResultItem {

    private Integer id;

    /** JSON: article_title */
    private String articleTitle;

    /** JSON: article_content */
    private String articleContent;
}
