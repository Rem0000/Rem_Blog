package com.mrzym.blog.dto;

import lombok.Data;

import java.util.List;

/**
 * 首页文章分页结果：{"current":1,"size":2,"list":[...],"total":27}
 *
 * 注意：这个接口的分页参数是**路径参数**（/blogHomeGetArticleList/:current/:size），
 * 不是 body 里的 page/pageSize，所以这里沿用 current/size 以保持契约。
 */
@Data
public class ArticleListResult {

    private int current;

    private int size;

    private List<ArticleListItem> list;

    private long total;

    public static ArticleListResult of(int current, int size, long total, List<ArticleListItem> list) {
        ArticleListResult r = new ArticleListResult();
        r.current = current;
        r.size = size;
        r.total = total;
        r.list = list;
        return r;
    }
}
