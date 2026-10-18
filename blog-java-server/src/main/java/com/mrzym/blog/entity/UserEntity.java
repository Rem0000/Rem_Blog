package com.mrzym.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户（blog_user）。password 只用于校验，任何接口都不直接返回实体。
 */
@Data
@TableName("blog_user")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String username;

    private String password;

    /** 1 管理员 2 普通用户 */
    private Integer role;

    private String nickName;

    private String qq;

    /** 库里存的是客户端原始 IP，展示时再转属地 */
    private String ip;

    private String avatar;

    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
