package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 新增/回复评论请求体。
 * add 用 author_id 发通知；apply 用 to_id/to_name 发通知。
 */
@Data
public class CommentAddForm {

    /** JSON: parent_id，回复时必传 */
    private Object parentId;

    /** 1 文章 2 说说 3 留言 */
    private Object type;

    /** JSON: for_id */
    private Object forId;

    /** JSON: from_id */
    private Object fromId;

    /** JSON: from_name */
    private String fromName;

    /** JSON: from_avatar */
    private String fromAvatar;

    /** JSON: to_id */
    private Object toId;

    /** JSON: to_name */
    private String toName;

    /** JSON: to_avatar */
    private String toAvatar;

    private String content;

    /** 只有新增评论时传，用于给作者发通知 */
    @JsonProperty("author_id")
    private Object authorId;
}
