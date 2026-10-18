package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论出参。字段顺序对齐现网：
 * 实体字段 -> ipAddress -> form_name（原实现的拼写错误，前端真的在读）-> is_like。
 *
 * form_name / is_like 在不需要的场景（如后台列表不带 is_like）要整个字段消失，
 * 所以标 NON_NULL。
 */
@Data
@JsonPropertyOrder({"createdAt", "updatedAt", "id", "parent_id", "type", "for_id", "from_id",
        "from_name", "from_avatar", "to_id", "to_name", "to_avatar", "content", "thumbs_up",
        "ip", "ipAddress", "form_name", "is_like"})
public class CommentView {

    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    private Integer id;

    /** JSON: parent_id */
    private Integer parentId;

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

    /** JSON 保持驼峰 */
    @JsonProperty("ipAddress")
    private String ipAddress;

    /** 原实现把 from_name 写成了 form_name，前端沿用了这个 key，必须保留 */
    @JsonProperty("form_name")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String formName;

    /** 不直接序列化，只通过下面的 getIsLike() 输出 is_like */
    @JsonIgnore
    private Boolean liked;

    /** JSON: is_like（下划线）；后台列表不设置它，字段就不出现 */
    @JsonProperty("is_like")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getIsLike() {
        return liked;
    }
}
