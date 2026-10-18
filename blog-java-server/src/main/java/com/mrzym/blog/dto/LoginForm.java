package com.mrzym.blog.dto;

import lombok.Data;

/** 登录请求体 */
@Data
public class LoginForm {

    private String username;

    private String password;
}
