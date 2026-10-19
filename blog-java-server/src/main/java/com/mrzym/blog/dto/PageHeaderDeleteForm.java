package com.mrzym.blog.dto;

import lombok.Data;

/**
 * 背景图删除请求体 {id, url}。
 * url 保留给前端；删除 OSS 旧图时以数据库里的 bg_url 为准，避免误删其他对象。
 */
@Data
public class PageHeaderDeleteForm {

    private Object id;

    private String url;
}
