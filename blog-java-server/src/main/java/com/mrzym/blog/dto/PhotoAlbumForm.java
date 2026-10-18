package com.mrzym.blog.dto;

import lombok.Data;

/** 相册新增/修改：{id?, album_name, album_cover, description} */
@Data
public class PhotoAlbumForm {

    private Object id;

    /** JSON: album_name */
    private String albumName;

    /** JSON: album_cover */
    private String albumCover;

    private String description;
}
