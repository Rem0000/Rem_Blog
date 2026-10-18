package com.mrzym.blog.dto;

import lombok.Data;

/** 热门文章项：{id, article_title, view_times} */
@Data
public class HotArticleItem {

    private Integer id;

    /** JSON: article_title */
    private String articleTitle;

    /** JSON: view_times */
    private Integer viewTimes;
}
