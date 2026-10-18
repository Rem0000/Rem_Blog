package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 批量删除请求体。
 *
 * 这两个字段是前端既有的驼峰命名，全局 SNAKE_CASE 会把它们转成
 * category_id_list / tag_id_list，所以必须显式覆盖。
 */
@Data
public class DeleteRequest {

    @JsonProperty("categoryIdList")
    private List<Integer> categoryIdList;

    @JsonProperty("tagIdList")
    private List<Integer> tagIdList;
}
