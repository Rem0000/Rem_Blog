package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** 新增文章响应：{article, articleTagList} */
@Data
public class ArticleCreateResult {

    private Object article;

    @JsonProperty("articleTagList")
    private List<ArticleTagLink> articleTagList;
}
