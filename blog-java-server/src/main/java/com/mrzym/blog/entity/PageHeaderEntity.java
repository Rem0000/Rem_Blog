package com.mrzym.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 页面背景图（blog_header）。
 *
 * 查询接口用的是投影 DTO（只出 id/route_name/bg_url），
 * 这个实体只服务于增删改。
 */
@Data
@TableName("blog_header")
public class PageHeaderEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** JSON: route_name */
    private String routeName;

    /** JSON: bg_url */
    private String bgUrl;

    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
