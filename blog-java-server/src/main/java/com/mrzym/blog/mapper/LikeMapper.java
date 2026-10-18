package com.mrzym.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mrzym.blog.entity.LikeEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 点赞记录 + 各业务表的点赞计数更新。
 * 文章计数用 ArticleMapper（已有），说说/留言/评论在这里。
 */
public interface LikeMapper extends BaseMapper<LikeEntity> {

    @Update("UPDATE blog_talk SET like_times = like_times + 1, updated_at = NOW() WHERE id = #{id}")
    int increaseTalkLike(@Param("id") Integer id);

    @Update("UPDATE blog_talk SET like_times = like_times - 1, updated_at = NOW() WHERE id = #{id}")
    int decreaseTalkLike(@Param("id") Integer id);

    @Update("UPDATE blog_message SET like_times = like_times + 1, updated_at = NOW() WHERE id = #{id}")
    int increaseMessageLike(@Param("id") Integer id);

    @Update("UPDATE blog_message SET like_times = like_times - 1, updated_at = NOW() WHERE id = #{id}")
    int decreaseMessageLike(@Param("id") Integer id);

    @Update("UPDATE blog_comment SET thumbs_up = thumbs_up + 1, updated_at = NOW() WHERE id = #{id}")
    int increaseCommentLike(@Param("id") Integer id);

    @Update("UPDATE blog_comment SET thumbs_up = thumbs_up - 1, updated_at = NOW() WHERE id = #{id}")
    int decreaseCommentLike(@Param("id") Integer id);
}
