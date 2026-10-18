package com.mrzym.blog.dto;

import lombok.Data;

/**
 * 背景图新增/修改请求体 {id, route_name, bg_url}。
 * id 可能是数字、空串或不存在，用 Object 承接。
 */
@Data
public class PageHeaderForm {

    private Object id;

    /** JSON: route_name */
    private String routeName;

    /** JSON: bg_url */
    private String bgUrl;
}
