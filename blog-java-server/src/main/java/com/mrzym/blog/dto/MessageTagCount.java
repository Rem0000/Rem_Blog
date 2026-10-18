package com.mrzym.blog.dto;

import lombok.Data;

/** 留言标签计数：{tag, count} */
@Data
public class MessageTagCount {

    private String tag;

    private Long count;
}
