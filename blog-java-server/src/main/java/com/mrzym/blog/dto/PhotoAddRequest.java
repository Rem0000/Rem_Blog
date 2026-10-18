package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** 批量新增图片：{photoList:[{album_id,url,status?}]} */
@Data
public class PhotoAddRequest {

    @JsonProperty("photoList")
    private List<PhotoItem> photoList;

    @Data
    public static class PhotoItem {

        /** JSON: album_id */
        private Object albumId;

        private String url;

        private Object status;
    }
}
