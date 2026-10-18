package com.mrzym.blog.dto;

import lombok.Data;

/** 修改密码：{password, password1, password2} */
@Data
public class UpdatePasswordForm {

    /** 旧密码 */
    private String password;

    /** 新密码 */
    private String password1;

    /** 确认新密码 */
    private String password2;
}
