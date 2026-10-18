package com.mrzym.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 网站设置（blog_config，实际只有一行）。
 *
 * 命名约定：
 *  - Java 字段驼峰，数据库列名下划线，由 MyBatis-Plus 的 table-underline 自动转换；
 *  - JSON 由全局 SNAKE_CASE 策略转换，所以不用逐个写 @JsonProperty。
 *  - 例外：createdAt / updatedAt 这两个字段前端在用（11 个文件），JSON 必须保持驼峰，
 *    因此显式覆盖。
 */
@Data
@TableName("blog_config")
public class ConfigEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String blogName;

    private String blogAvatar;

    private String avatarBg;

    private String personalSay;

    private String blogNotice;

    private String qqLink;

    private String weChatLink;

    private String githubLink;

    private String gitEeLink;

    private String bilibiliLink;

    private Long viewTime;

    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    private String weChatGroup;

    private String qqGroup;

    private String weChatPay;

    private String aliPay;
}
