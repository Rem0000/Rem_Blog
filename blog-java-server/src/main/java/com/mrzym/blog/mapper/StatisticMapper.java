package com.mrzym.blog.mapper;

import com.mrzym.blog.dto.StatisticResult;
import org.apache.ibatis.annotations.Select;

public interface StatisticMapper {

    /** 用一条 SQL 取四个总数，省三次往返 */
    @Select("SELECT "
            + "(SELECT COUNT(*) FROM blog_article) AS articleCount, "
            + "(SELECT COUNT(*) FROM blog_tag) AS tagCount, "
            + "(SELECT COUNT(*) FROM blog_category) AS categoryCount, "
            + "(SELECT COUNT(*) FROM blog_user) AS userCount")
    StatisticResult selectStatistic();
}
