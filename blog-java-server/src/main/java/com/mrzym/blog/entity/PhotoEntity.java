package com.mrzym.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.time.LocalDateTime;

/** 相册图片（blog_photo） */
@Data
@TableName("blog_photo")
@JsonPropertyOrder({"createdAt", "updatedAt", "id", "album_id", "url", "status"})
public class PhotoEntity {

    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** JSON: album_id */
    private Integer albumId;

    private String url;

    /** 1 正常 2 回收站 */
    private Integer status;
}
