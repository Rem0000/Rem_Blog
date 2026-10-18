package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 用户信息：{"id":1,"role":1,"nick_name":"...","qq":"","avatar":"...","ipAddress":"四川"}
 *
 * 注意 ipAddress 是驼峰，前端在用；数据库列名是 ip。
 * 这里用投影 DTO，避免把 username / password 带出去。
 */
@Data
public class UserInfo {

    private Integer id;

    private Integer role;

    /** JSON: nick_name */
    private String nickName;

    private String qq;

    private String avatar;

    /** JSON 保持驼峰 */
    @JsonProperty("ipAddress")
    private String ipAddress;
}
