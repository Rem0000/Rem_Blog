package com.mrzym.blog.dto;

import lombok.Data;

/** 前台评论分页：{current, size, type, for_id, user_id, order, parent_id} */
@Data
public class CommentFrontQuery {

    private Object current;

    private Object size;

    private Object type;

    /** JSON: for_id */
    private Object forId;

    /** JSON: user_id */
    private Object userId;

    /** new = 按时间倒序，其它 = 按点赞数倒序 */
    private String order;

    /** JSON: parent_id */
    private Object parentId;
}
