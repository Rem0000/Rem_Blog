package com.mrzym.blog.dto;

import lombok.Data;

/** 用户修改自己的信息：{avatar, nick_name, qq} */
@Data
public class UpdateOwnUserInfoForm {

    private String avatar;

    /** JSON: nick_name */
    private String nickName;

    private String qq;
}
