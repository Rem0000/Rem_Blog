package com.mrzym.blog.dto;

import lombok.Data;

/** 文章表单里的标签：{id, tag_name} */
@Data
public class ArticleTagForm {

    private Object id;

    /** JSON: tag_name */
    private String tagName;
}
