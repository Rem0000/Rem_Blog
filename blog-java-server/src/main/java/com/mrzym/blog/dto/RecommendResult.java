package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 推荐文章：{previous, next, recommend}；previous/next 只有 id/标题/封面 */
@Data
public class RecommendResult {

    private ArticleNeighbor previous;

    private ArticleNeighbor next;

    private List<ArticleBrief> recommend;

    @Data
    @JsonPropertyOrder({"id", "article_title", "article_cover"})
    public static class ArticleNeighbor {

        private Integer id;

        /** JSON: article_title */
        private String articleTitle;

        /** JSON: article_cover */
        private String articleCover;
    }
}
