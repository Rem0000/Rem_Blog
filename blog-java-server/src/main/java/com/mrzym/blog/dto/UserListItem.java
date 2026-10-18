package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户列表项：createdAt/updatedAt + 下划线字段 + ip_address（注意这里是下划线，
 * 与 getUserInfoById 的 ipAddress 不同，前端两个页面读的 key 不一样）。
 */
@Data
@JsonPropertyOrder({"createdAt", "updatedAt", "id", "username", "role", "nick_name", "qq",
        "ip", "avatar", "ip_address"})
public class UserListItem {

    @JsonProperty("createdAt")
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    private Integer id;

    private String username;

    private Integer role;

    /** JSON: nick_name */
    private String nickName;

    private String qq;

    private String ip;

    private String avatar;

    /** 全局 SNAKE_CASE 正好输出 ip_address */
    private String ipAddress;
}
