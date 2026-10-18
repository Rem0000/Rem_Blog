package com.mrzym.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.time.LocalDateTime;

/** 点赞记录（blog_like） */
@Data
@TableName("blog_like")
@JsonPropertyOrder({"createdAt", "updatedAt", "id", "type", "for_id", "user_id", "ip"})
public class LikeEntity {

    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 1 文章 2 说说 3 留言 4 评论 */
    private Integer type;

    /** JSON: for_id */
    private Integer forId;

    /** JSON: user_id，游客点赞时为空 */
    private Integer userId;

    private String ip;
}
