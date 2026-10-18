package com.mrzym.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mrzym.blog.dto.MessageTagCount;
import com.mrzym.blog.entity.MessageEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface MessageMapper extends BaseMapper<MessageEntity> {

    @Update("UPDATE blog_message SET like_times = like_times + 1, updated_at = NOW() WHERE id = #{id}")
    int increaseLike(@Param("id") Integer id);

    @Update("UPDATE blog_message SET like_times = like_times - 1, updated_at = NOW() WHERE id = #{id}")
    int decreaseLike(@Param("id") Integer id);

    /** 热门标签：tag 计数倒序，最多 10 个 */
    @Select("SELECT tag, COUNT(*) AS count FROM blog_message "
            + "WHERE tag IS NOT NULL AND tag <> '' GROUP BY tag ORDER BY count DESC LIMIT 10")
    List<MessageTagCount> selectHotTags();
}
