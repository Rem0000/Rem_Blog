package com.mrzym.blog.dto;

import lombok.Data;

/** 相册图片分页：{current, size, id, status} */
@Data
public class PhotoQuery {

    private Object current;

    private Object size;

    /** 相册 id */
    private Object id;

    private Object status;
}
