package com.mrzym.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 说说图片（blog_talk_photo） */
@Data
@TableName("blog_talk_photo")
public class TalkPhotoEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** JSON: talk_id */
    private Integer talkId;

    private String url;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
