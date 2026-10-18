package com.mrzym.blog.dto;

import lombok.Data;

/** 标签 id + 名称（内部组装用，不直接对外） */
@Data
public class TagBrief {

    private Integer id;

    /** JSON: tag_name */
    private String tagName;
}
