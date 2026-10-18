package com.mrzym.blog.dto;

import lombok.Data;

import java.util.List;

/**
 * 聊天分页响应。原实现的 size 是“本次返回条数”（list.length），不是请求的 size；
 * list 里还可能出现 null（user_id 为空的历史数据），所以用 List<Object>。
 */
@Data
public class ChatPageResult {

    private Object current;

    private int size;

    private long total;

    private List<Object> list;
}
