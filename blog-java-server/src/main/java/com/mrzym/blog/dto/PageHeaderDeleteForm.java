package com.mrzym.blog.dto;

import lombok.Data;

/**
 * 背景图删除请求体 {id, url}。
 * url 只在 qiniu/minio 模式下用于远程删图，本批先不用。
 */
@Data
public class PageHeaderDeleteForm {

    private Object id;

    private String url;
}
