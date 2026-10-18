package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 说说列表项。字段顺序照现网：
 * createdAt, updatedAt, id, content, user_id, status, is_top, like_times,
 * talkImgList, nick_name, avatar, is_like
 *
 * talkImgList / nick_name / avatar / is_like 都只在有值时才出现（NON_NULL），
 * 因为原实现里“没有图片/用户查不到/后台列表”时这些 key 是缺失的。
 */
@Data
@JsonPropertyOrder({"createdAt", "updatedAt", "id", "content", "user_id", "status", "is_top",
        "like_times", "talkImgList", "nick_name", "avatar", "is_like"})
public class TalkView {

    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    private Integer id;

    private String content;

    /** JSON: user_id */
    private Integer userId;

    private Integer status;

    /** JSON: is_top */
    private Integer isTop;

    /** JSON: like_times */
    private Integer likeTimes;

    @JsonProperty("talkImgList")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> talkImgList;

    /** JSON: nick_name */
    @JsonProperty("nick_name")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String nickName;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String avatar;

    @JsonProperty("is_like")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean isLike;
}
