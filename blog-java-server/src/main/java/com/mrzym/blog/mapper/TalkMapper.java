package com.mrzym.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mrzym.blog.entity.TalkEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface TalkMapper extends BaseMapper<TalkEntity> {

    @Update("UPDATE blog_talk SET like_times = like_times + 1, updated_at = NOW() WHERE id = #{id}")
    int increaseLike(@Param("id") Integer id);

    @Update("UPDATE blog_talk SET like_times = like_times - 1, updated_at = NOW() WHERE id = #{id}")
    int decreaseLike(@Param("id") Integer id);
}
