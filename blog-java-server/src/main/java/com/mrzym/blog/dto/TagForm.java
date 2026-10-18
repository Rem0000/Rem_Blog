package com.mrzym.blog.dto;

import lombok.Data;

/** 新增/修改标签请求体：{id, tag_name} */
@Data
public class TagForm {

    private Integer id;

    /** JSON: tag_name（全局下划线策略） */
    private String tagName;
}
