package com.mrzym.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 说说（blog_talk） */
@Data
@TableName("blog_talk")
public class TalkEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String content;

    /** JSON: user_id */
    private Integer userId;

    /** 1 公开 2 私密 3 回收站 */
    private Integer status;

    /** JSON: is_top，1 置顶 2 不置顶 */
    private Integer isTop;

    /** JSON: like_times */
    private Integer likeTimes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
