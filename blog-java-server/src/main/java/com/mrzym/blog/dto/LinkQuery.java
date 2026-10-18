package com.mrzym.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 友链分页查询体。
 *
 * 现网契约是 current / size（前台 link-list.vue 与后台 links/index.ts 都在传），
 * 响应也是 {current, size, list, total}；已实测 mrzym.top 同此。
 *
 * 另外顺手收 page / pageSize 作为别名（README 早期约定的写法），
 * 两者同时存在时以 current / size 为准。
 *
 * time 是区间数组 ["开始","结束"]，也可能传空串或空数组，所以用 Object 收。
 */
@Data
public class LinkQuery {

    private Object current;

    private Object size;

    /** 别名，兼容 page/pageSize 写法；全局 SNAKE_CASE 对反序列化也生效，必须显式覆盖 */
    private Object page;

    @JsonProperty("pageSize")
    private Object pageSize;

    private Object time;

    private Object status;

    /** JSON: site_name */
    private String siteName;
}
