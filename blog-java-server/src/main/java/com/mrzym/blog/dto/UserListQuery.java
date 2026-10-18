package com.mrzym.blog.dto;

import lombok.Data;

/**
 * 用户分页查询：{current, size, nick_name, role}
 * 注意 role 只有是数字且非 0 时才过滤（原实现 `typeof role === "number"`）。
 */
@Data
public class UserListQuery {

    private Object current;

    private Object size;

    /** JSON: nick_name */
    private String nickName;

    private Object role;
}
