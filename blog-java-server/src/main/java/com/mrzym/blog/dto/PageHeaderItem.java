package com.mrzym.blog.dto;

import lombok.Data;

/**
 * 背景图项：{"id":8,"route_name":"Archives","bg_url":"..."}
 *
 * 线上只返回这三个字段（Node 端限制了 attributes），不带时间戳，所以这里用投影 DTO
 * 而不是直接返回实体。route_name / bg_url 由全局下划线策略自动转换。
 */
@Data
public class PageHeaderItem {

    private Integer id;

    private String routeName;

    private String bgUrl;
}
