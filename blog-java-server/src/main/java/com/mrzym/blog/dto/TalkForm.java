package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** 说说新增/修改：{id?, content, user_id, status, is_top, talkImgList:[{url}]} */
@Data
public class TalkForm {

    private Object id;

    private String content;

    /** JSON: user_id */
    private Object userId;

    private Object status;

    /** JSON: is_top */
    private Object isTop;

    @JsonProperty("talkImgList")
    private List<TalkImgForm> talkImgList;

    /** 图片项：{url} */
    @Data
    public static class TalkImgForm {
        private String url;
    }
}
