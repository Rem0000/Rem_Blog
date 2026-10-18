package com.mrzym.blog.dto;

import lombok.Data;

/** 标题查重请求体：{id?, article_title} */
@Data
public class ArticleTitleQuery {

    private Object id;

    /** JSON: article_title */
    private String articleTitle;
}
