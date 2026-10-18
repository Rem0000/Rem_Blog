package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 留言新增/修改：{id?, message, color, font_size, font_weight, bg_color, bg_url, tag, user_id?, nick_name?} */
@Data
public class MessageForm {

    private Object id;

    private String message;

    private String color;

    /** JSON: font_size */
    private Object fontSize;

    /** JSON: font_weight */
    private Object fontWeight;

    /** JSON: bg_color */
    private String bgColor;

    /** JSON: bg_url */
    private String bgUrl;

    /** JSON: user_id */
    private Object userId;

    /** JSON: nick_name */
    private String nickName;

    private String tag;
}
