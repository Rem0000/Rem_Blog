package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 标签分页查询体。
 *
 * 与分类一致：现网/前端用 current / size，响应 {current, size, list, total}；
 * page / pageSize 仅作为兼容别名保留。
 */
@Data
public class TagQuery {

    private Object current;

    private Object size;

    private Object page;

    @JsonProperty("pageSize")
    private Object pageSize;

    /** JSON: tag_name */
    private String tagName;
}
