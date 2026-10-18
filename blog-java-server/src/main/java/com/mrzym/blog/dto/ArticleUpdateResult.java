package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** 修改文章响应：{res, newArticleTagList} */
@Data
public class ArticleUpdateResult {

    private boolean res;

    @JsonProperty("newArticleTagList")
    private List<ArticleTagLink> newArticleTagList;
}
