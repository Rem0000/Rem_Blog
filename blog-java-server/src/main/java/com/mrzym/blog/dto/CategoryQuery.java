package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 分类分页查询体：{page, pageSize, category_name}
 *
 * pageSize 必须显式覆盖：全局 SNAKE_CASE 策略对反序列化同样生效，
 * 不覆盖的话 Jackson 会去找 page_size，收不到前端传的 pageSize。
 * page/pageSize 用 Object 承接，传字符串或数字都能收，也方便原样回显。
 */
@Data
public class CategoryQuery {

    private Object page;

    @JsonProperty("pageSize")
    private Object pageSize;

    /** JSON: category_name */
    private String categoryName;
}
