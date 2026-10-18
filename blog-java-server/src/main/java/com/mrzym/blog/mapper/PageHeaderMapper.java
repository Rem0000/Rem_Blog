package com.mrzym.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mrzym.blog.dto.PageHeaderItem;
import com.mrzym.blog.entity.PageHeaderEntity;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface PageHeaderMapper extends BaseMapper<PageHeaderEntity> {

    /** 只取线上响应用到的三列，不带时间戳 */
    @Select("SELECT id, route_name, bg_url FROM blog_header")
    List<PageHeaderItem> selectAll();
}
