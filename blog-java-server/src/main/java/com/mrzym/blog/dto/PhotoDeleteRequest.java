package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** 批量删除图片：{imgList:[{id,url}], type}；type 1 进回收站，其它永久删除 */
@Data
public class PhotoDeleteRequest {

    @JsonProperty("imgList")
    private List<ImgItem> imgList;

    /** 1 回收站 2 永久删除 */
    private Object type;

    @Data
    public static class ImgItem {
        private Object id;
        private String url;
    }
}
