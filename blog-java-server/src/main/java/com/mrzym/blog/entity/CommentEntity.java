package com.mrzym.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.time.LocalDateTime;

/** 评论（blog_comment） */
@Data
@TableName("blog_comment")
@JsonPropertyOrder({"createdAt", "updatedAt", "id", "parent_id", "type", "for_id", "from_id",
        "from_name", "from_avatar", "to_id", "to_name", "to_avatar", "content", "thumbs_up", "ip"})
public class CommentEntity {

    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** JSON: parent_id，父评论 id（父评论为 null） */
    private Integer parentId;

    /** 1 文章 2 说说 3 留言 */
    private Integer type;

    /** JSON: for_id */
    private Integer forId;

    /** JSON: from_id */
    private Integer fromId;

    /** JSON: from_name */
    private String fromName;

    /** JSON: from_avatar */
    private String fromAvatar;

    /** JSON: to_id */
    private Integer toId;

    /** JSON: to_name */
    private String toName;

    /** JSON: to_avatar */
    private String toAvatar;

    private String content;

    /** JSON: thumbs_up */
    private Integer thumbsUp;

    private String ip;
}
