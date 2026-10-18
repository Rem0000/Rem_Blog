package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.util.List;

/**
 * 说说详情。注意现网的详情接口是把 Sequelize 的 Date 直接展开（`{...dataValues}`），
 * createdAt/updatedAt 输出成了 ISO UTC 字符串（带 T 和 Z、毫秒），
 * 和列表接口的 "yyyy-MM-dd HH:mm:ss" 不一样，这里必须单独格式化。
 */
@Data
@JsonPropertyOrder({"id", "content", "user_id", "status", "is_top", "like_times",
        "createdAt", "updatedAt", "talkImgList"})
public class TalkDetail {

    private Integer id;

    private String content;

    /** JSON: user_id */
    private Integer userId;

    private Integer status;

    /** JSON: is_top */
    private Integer isTop;

    /** JSON: like_times */
    private Integer likeTimes;

    /** 已经是格式化好的 ISO UTC 字符串 */
    @JsonProperty("createdAt")
    private String createdAt;

    @JsonProperty("updatedAt")
    private String updatedAt;

    @JsonProperty("talkImgList")
    private List<String> talkImgList;
}
