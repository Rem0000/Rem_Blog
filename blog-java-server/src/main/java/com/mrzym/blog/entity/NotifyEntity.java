package com.mrzym.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息通知（blog_notify）。本批只用到写入（友链申请通知），
 * 查询/已阅/删除在 notify 模块再补。
 */
@Data
@TableName("blog_notify")
public class NotifyEntity {

    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String message;

    /** JSON: user_id，通知给谁 */
    private Integer userId;

    /** 1 文章 2 说说 3 留言 4 友链 */
    private Integer type;

    /** JSON: to_id，说说/文章 id，用于跳转 */
    private Integer toId;

    /** JSON: is_view，1 未读 2 已读 */
    private Integer isView;
}
