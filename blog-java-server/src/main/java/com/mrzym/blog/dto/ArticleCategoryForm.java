package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 文章表单里的分类：{id, category_name} */
@Data
public class ArticleCategoryForm {

    private Object id;

    /** JSON: category_name */
    private String categoryName;
}
