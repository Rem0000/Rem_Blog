package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 标签分页查询体：{page, pageSize, tag_name} */
@Data
public class TagQuery {

    private Object page;

    @JsonProperty("pageSize")
    private Object pageSize;

    /** JSON: tag_name */
    private String tagName;
}
