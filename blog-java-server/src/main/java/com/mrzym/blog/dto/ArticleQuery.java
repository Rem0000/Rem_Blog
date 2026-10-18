package com.mrzym.blog.dto;

import lombok.Data;

/**
 * 后台文章分页查询体。
 * JSON 名：current / size / article_title / is_top / status / tag_id / category_id / create_time
 */
@Data
public class ArticleQuery {

    private Object current;

    private Object size;

    /** JSON: article_title */
    private String articleTitle;

    /** JSON: is_top */
    private Object isTop;

    private Object status;

    /** JSON: tag_id */
    private Object tagId;

    /** JSON: category_id */
    private Object categoryId;

    /** JSON: create_time，区间数组 */
    private Object createTime;
}
