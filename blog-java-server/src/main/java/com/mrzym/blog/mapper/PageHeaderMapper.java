package com.mrzym.blog.mapper;

import com.mrzym.blog.dto.PageHeaderItem;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface PageHeaderMapper {

    /** 只取线上响应用到的三列，不带时间戳 */
    @Select("SELECT id, route_name, bg_url FROM blog_header")
    List<PageHeaderItem> selectAll();
}
