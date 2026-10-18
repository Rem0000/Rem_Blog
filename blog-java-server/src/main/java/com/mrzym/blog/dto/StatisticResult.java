package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 数据统计：{"articleCount":27,"tagCount":25,"categoryCount":6,"userCount":1091}
 * 四个字段都是驼峰，必须显式覆盖全局下划线策略。
 */
@Data
public class StatisticResult {

    @JsonProperty("articleCount")
    private Long articleCount;

    @JsonProperty("tagCount")
    private Long tagCount;

    @JsonProperty("categoryCount")
    private Long categoryCount;

    @JsonProperty("userCount")
    private Long userCount;
}
