package com.mrzym.blog.dto;

import lombok.Data;

/**
 * 友链分页查询体。
 *
 * 这里是**现网契约**：入参是 current / size（不是 page / pageSize），
 * 响应也是 {current, size, list, total}。前台 link-list.vue 与后台
 * links/index.ts 都在传 current / size，实测 mrzym.top 同此。
 *
 * time 是区间数组 ["开始","结束"]，也可能传空串或空数组，所以用 Object 收。
 */
@Data
public class LinkQuery {

    private Object current;

    private Object size;

    private Object time;

    private Object status;

    /** JSON: site_name */
    private String siteName;
}
