package com.mrzym.blog.dto;

import lombok.Data;

/** 说说分页：{current, size, status, user_id} */
@Data
public class TalkQuery {

    private Object current;

    private Object size;

    private Object status;

    /** JSON: user_id，前台用来判断 is_like */
    private Object userId;
}
