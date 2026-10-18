package com.mrzym.blog.mapper;

import com.mrzym.blog.dto.UserInfo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserMapper {

    /**
     * 只查公开字段，绝不带 password / username。
     * 数据库列 ip 要别名成 ipAddress，前端读的是驼峰。
     */
    @Select("SELECT id, role, nick_name, qq, avatar, ip AS ipAddress FROM blog_user WHERE id = #{id}")
    UserInfo selectInfoById(@Param("id") Integer id);
}
