package com.mrzym.blog.dto;

import lombok.Data;

/** 相册分页：{current, size, album_name} */
@Data
public class PhotoAlbumQuery {

    private Object current;

    private Object size;

    /** JSON: album_name */
    private String albumName;
}
