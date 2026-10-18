package com.mrzym.blog.dto;

import lombok.Data;

/** 注册请求体：{username, password, nick_name?, qq?} */
@Data
public class RegisterForm {

    private String username;

    private String password;

    /** JSON: nick_name */
    private String nickName;

    private String qq;
}
