package com.mrzym.blog.dto;

import lombok.Data;

/** 文章与标签名的扁平结果，用于组装 tagNameList */
@Data
public class ArticleTagRow {

    private Integer articleId;

    private String tagName;
}
