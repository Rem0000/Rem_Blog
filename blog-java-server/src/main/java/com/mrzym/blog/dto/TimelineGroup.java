package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** 时间轴分组：{"year":"2025","articleList":[...]} */
@Data
public class TimelineGroup {

    private String year;

    /** JSON 保持驼峰 */
    @JsonProperty("articleList")
    private List<ArticleBrief> articleList;
}
