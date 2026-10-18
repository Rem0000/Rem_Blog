package com.mrzym.blog.dto;

import lombok.Data;

/** 点赞/取消点赞/是否点赞：{for_id, type, user_id?} */
@Data
public class LikeForm {

    /** JSON: for_id */
    private Object forId;

    /** 1 文章 2 说说 3 留言 4 评论 */
    private Object type;

    /** JSON: user_id，空表示按 IP 维度 */
    private Object userId;
}
