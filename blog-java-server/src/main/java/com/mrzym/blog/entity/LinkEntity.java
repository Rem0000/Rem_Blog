package com.mrzym.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 友链（blog_links）。
 *
 * 字段声明顺序与现网响应一致：createdAt / updatedAt 在最前，
 * 然后是 id、下划线字段（Sequelize 返回 dataValues 时就是这个顺序）；
 * 再加 @JsonPropertyOrder 锁死，避免 Jackson 按 getter 顺序重排。
 */
@Data
@TableName("blog_links")
@JsonPropertyOrder({"createdAt", "updatedAt", "id", "site_name", "site_desc",
        "site_avatar", "url", "status", "user_id"})
public class LinkEntity {

    /** JSON 保持驼峰，前端在用 */
    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** JSON: site_name */
    private String siteName;

    /** JSON: site_desc */
    private String siteDesc;

    /** JSON: site_avatar */
    private String siteAvatar;

    private String url;

    /** 1 待审核 2 审核通过 */
    private Integer status;

    /** 申请者 id，库里是 varchar */
    private String userId;
}
