package com.mrzym.blog.dto;

import lombok.Data;

/** 管理员改用户信息：{id, nick_name, avatar} */
@Data
public class AdminUpdateUserInfoForm {

    private Object id;

    /** JSON: nick_name */
    private String nickName;

    private String avatar;
}
