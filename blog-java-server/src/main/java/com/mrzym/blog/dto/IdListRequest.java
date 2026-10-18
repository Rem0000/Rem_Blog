package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 批量操作请求体 {idList:[...]}。
 *
 * idList 全局 SNAKE_CASE 会被转成 id_list，前端传的是驼峰，必须显式覆盖
 * （与 DeleteRequest 里的 categoryIdList 同理）。
 */
@Data
public class IdListRequest {

    @JsonProperty("idList")
    private List<Integer> idList;
}
