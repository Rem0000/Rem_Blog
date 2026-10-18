package com.mrzym.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mrzym.blog.entity.CommentEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface CommentMapper extends BaseMapper<CommentEntity> {

    /** 点赞：thumbs_up + 1，同时刷新 updated_at（Sequelize increment 的行为） */
    @Update("UPDATE blog_comment SET thumbs_up = thumbs_up + 1, updated_at = NOW() WHERE id = #{id}")
    int increaseThumbsUp(@Param("id") Integer id);

    @Update("UPDATE blog_comment SET thumbs_up = thumbs_up - 1, updated_at = NOW() WHERE id = #{id}")
    int decreaseThumbsUp(@Param("id") Integer id);
}
