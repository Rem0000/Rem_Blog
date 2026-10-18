package com.mrzym.blog.mapper;

import com.mrzym.blog.dto.ArticleListItem;
import com.mrzym.blog.dto.ArticleTagRow;
import com.mrzym.blog.dto.CategoryBrief;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ArticleMapper {

    @Select("SELECT COUNT(*) FROM blog_article WHERE status = 1")
    long countPublished();

    /**
     * 首页列表：只取列表需要的列（排除 article_content / origin_url 这两个大字段），
     * 排序与原实现一致：置顶优先 -> 置顶顺序 -> 创建时间倒序。
     * order 是 MySQL 保留字，必须加反引号。
     */
    @Select("SELECT id, article_title, author_id, category_id, article_description, article_cover, "
            + "is_top, `order`, status, type, created_at, updated_at, view_times, thumbs_up_times, reading_duration "
            + "FROM blog_article WHERE status = 1 "
            + "ORDER BY is_top ASC, `order` ASC, created_at DESC "
            + "LIMIT #{offset}, #{size}")
    List<ArticleListItem> selectHomePage(@Param("offset") int offset, @Param("size") int size);

    @Select("<script>"
            + "SELECT id, category_name FROM blog_category WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<CategoryBrief> selectCategoryNames(@Param("ids") List<Integer> ids);

    @Select("<script>"
            + "SELECT at.article_id AS articleId, t.tag_name AS tagName "
            + "FROM blog_article_tag at JOIN blog_tag t ON t.id = at.tag_id WHERE at.article_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<ArticleTagRow> selectTagNames(@Param("ids") List<Integer> ids);
}
