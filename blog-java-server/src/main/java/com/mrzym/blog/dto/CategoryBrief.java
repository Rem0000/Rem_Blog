package com.mrzym.blog.dto;

import lombok.Data;

/** 分类 id -> 名称，用于文章列表补 categoryName */
@Data
public class CategoryBrief {

    private Integer id;

    /** JSON 不直接输出，仅内部使用 */
    private String categoryName;
}
