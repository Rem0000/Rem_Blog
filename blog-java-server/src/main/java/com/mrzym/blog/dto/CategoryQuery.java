package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 分类分页查询体。
 *
 * 现网/前端实际用的是 current / size（后台 category/index.tsx 的 param 就是这两个），
 * 响应也是 {current, size, list, total}；page / pageSize 仅作为兼容别名保留。
 */
@Data
public class CategoryQuery {

    private Object current;

    private Object size;

    private Object page;

    @JsonProperty("pageSize")
    private Object pageSize;

    /** JSON: category_name */
    private String categoryName;
}
