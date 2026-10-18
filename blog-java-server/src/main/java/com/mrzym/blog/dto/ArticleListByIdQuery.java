package com.mrzym.blog.dto;

import lombok.Data;

/** 按标签/分类分页的请求体：{id, current, size} */
@Data
public class ArticleListByIdQuery {

    private Object id;

    private Object current;

    private Object size;
}
