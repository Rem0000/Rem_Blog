package com.mrzym.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mrzym.blog.dto.ArticleBrief;
import com.mrzym.blog.dto.ArticleListItem;
import com.mrzym.blog.dto.ArticleTagRow;
import com.mrzym.blog.dto.CategoryBrief;
import com.mrzym.blog.dto.HotArticleItem;
import com.mrzym.blog.dto.TagBrief;
import com.mrzym.blog.entity.ArticleEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ArticleMapper extends BaseMapper<ArticleEntity> {

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

    /** 文章标签 id，按关联表自增顺序（原实现 getTagListByArticleId 的顺序） */
    @Select("SELECT tag_id FROM blog_article_tag WHERE article_id = #{articleId} ORDER BY id ASC")
    List<Integer> selectTagIds(@Param("articleId") Integer articleId);

    @Select("SELECT t.id AS id, t.tag_name AS tagName FROM blog_article_tag at "
            + "JOIN blog_tag t ON t.id = at.tag_id WHERE at.article_id = #{articleId} ORDER BY at.id ASC")
    List<TagBrief> selectTagsByArticleId(@Param("articleId") Integer articleId);

    @Select("SELECT id, article_title, article_cover, created_at FROM blog_article "
            + "WHERE status = 1 ORDER BY created_at DESC LIMIT #{offset}, #{size}")
    List<ArticleBrief> selectTimelinePage(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT id, article_title, article_content, view_times FROM blog_article "
            + "WHERE article_content LIKE CONCAT('%', #{content}, '%') AND status = 1 "
            + "ORDER BY view_times DESC LIMIT 8")
    List<ArticleEntity> searchByContent(@Param("content") String content);

    @Select("SELECT id, article_title, view_times FROM blog_article "
            + "WHERE status = 1 ORDER BY view_times DESC LIMIT 5")
    List<HotArticleItem> selectHot();

    @Update("UPDATE blog_article SET view_times = view_times + 1 WHERE id = #{id}")
    int incrementViewTimes(@Param("id") Integer id);

    @Update("UPDATE blog_article SET thumbs_up_times = thumbs_up_times + 1 WHERE id = #{id}")
    int incrementThumbsUp(@Param("id") Integer id);

    @Update("UPDATE blog_article SET thumbs_up_times = thumbs_up_times - 1 WHERE id = #{id}")
    int decrementThumbsUp(@Param("id") Integer id);

    @Update("UPDATE blog_article SET reading_duration = reading_duration + #{duration} WHERE id = #{id}")
    int increaseReadingDuration(@Param("id") Integer id, @Param("duration") double duration);

    /** 历史数据封面域名整体迁移（原实现 updateUrl），返回受影响行数 */
    @Update("UPDATE blog_article SET article_cover = REPLACE(article_cover, 'http://img.mrzym.top/', "
            + "'http://mrzym.top/online/') WHERE article_cover LIKE 'http://img.mrzym.top/%'")
    int replaceCoverDomain();
}
