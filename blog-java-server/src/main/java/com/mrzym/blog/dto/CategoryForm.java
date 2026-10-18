package com.mrzym.blog.dto;

import lombok.Data;

/** 新增/修改分类请求体：{id, category_name} */
@Data
public class CategoryForm {

    private Integer id;

    /** JSON: category_name（全局下划线策略） */
    private String categoryName;
}
