package com.mrzym.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mrzym.blog.dto.UserInfo;
import com.mrzym.blog.entity.UserEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserMapper extends BaseMapper<UserEntity> {

    /**
     * 只查公开字段，绝不带 password / username。
     * 数据库列 ip 要别名成 ipAddress，前端读的是驼峰。
     */
    @Select("SELECT id, role, nick_name, qq, avatar, ip AS ipAddress FROM blog_user WHERE id = #{id}")
    UserInfo selectInfoById(@Param("id") Integer id);

    /** 文章详情里的 authorName，对应原实现 getAuthorNameById */
    @Select("SELECT nick_name FROM blog_user WHERE id = #{id}")
    String selectNickNameById(@Param("id") Integer id);

    /** 登录/注册查重需要带 password */
    @Select("SELECT id, username, password, role, nick_name, qq, ip, avatar, created_at, updated_at "
            + "FROM blog_user WHERE username = #{username} LIMIT 1")
    UserEntity selectByUsername(@Param("username") String username);
}
