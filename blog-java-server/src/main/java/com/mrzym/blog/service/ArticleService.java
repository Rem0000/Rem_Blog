package com.mrzym.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mrzym.blog.common.CurrentPageResult;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.dto.ArticleBrief;
import com.mrzym.blog.dto.ArticleCreateResult;
import com.mrzym.blog.dto.ArticleDetail;
import com.mrzym.blog.dto.ArticleForm;
import com.mrzym.blog.dto.ArticleListItem;
import com.mrzym.blog.dto.ArticleListResult;
import com.mrzym.blog.dto.ArticleQuery;
import com.mrzym.blog.dto.ArticleTagForm;
import com.mrzym.blog.dto.ArticleTagLink;
import com.mrzym.blog.dto.ArticleTagRow;
import com.mrzym.blog.dto.ArticleUpdateResult;
import com.mrzym.blog.dto.CategoryBrief;
import com.mrzym.blog.dto.HotArticleItem;
import com.mrzym.blog.dto.RecommendResult;
import com.mrzym.blog.dto.SearchResultItem;
import com.mrzym.blog.dto.TagBrief;
import com.mrzym.blog.dto.TimelineGroup;
import com.mrzym.blog.dto.TimelineResult;
import com.mrzym.blog.entity.ArticleEntity;
import com.mrzym.blog.entity.ArticleTagEntity;
import com.mrzym.blog.entity.CategoryEntity;
import com.mrzym.blog.entity.TagEntity;
import com.mrzym.blog.mapper.ArticleMapper;
import com.mrzym.blog.mapper.ArticleTagMapper;
import com.mrzym.blog.mapper.CategoryMapper;
import com.mrzym.blog.mapper.TagMapper;
import com.mrzym.blog.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 文章模块，对齐原实现 service/article/index.js + controller/article/index.js。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final UserMapper userMapper;

    // ==================== 读 ====================

    /**
     * 首页文章列表（路径分页，current/size 原样回显字符串）。
     */
    public ArticleListResult getHomeList(String current, String size) {
        int pageNo = Math.max(Num.toInt(current, 1), 1);
        int pageSize = Num.toInt(size, 10);
        if (pageSize <= 0) {
            pageSize = 10;
        }
        int offset = (pageNo - 1) * pageSize;

        long total = articleMapper.countPublished();
        List<ArticleListItem> list = articleMapper.selectHomePage(offset, pageSize);
        enrichListItems(list);
        return ArticleListResult.of(current, size, total, list);
    }

    /**
     * 时间轴：按年分组，返回 [{year, articleList}]。
     */
    public TimelineResult getTimeline(String current, String size) {
        int pageNo = Math.max(Num.toInt(current, 1), 1);
        int pageSize = Num.toInt(size, 10);
        if (pageSize <= 0) {
            pageSize = 10;
        }
        int offset = (pageNo - 1) * pageSize;

        long total = articleMapper.countPublished();
        List<ArticleBrief> rows = articleMapper.selectTimelinePage(offset, pageSize);

        Map<String, List<ArticleBrief>> grouped = new LinkedHashMap<>();
        for (ArticleBrief row : rows) {
            String year = "year_" + (row.getCreatedAt() == null ? "" : row.getCreatedAt().getYear());
            grouped.computeIfAbsent(year, k -> new ArrayList<>()).add(row);
        }
        List<TimelineGroup> groups = new ArrayList<>();
        grouped.forEach((key, value) -> {
            TimelineGroup group = new TimelineGroup();
            group.setYear(key.substring("year_".length()));
            group.setArticleList(value);
            groups.add(group);
        });
        return TimelineResult.of(current, size, total, groups);
    }

    /** 按标签分页（只出 id/标题/封面/创建时间） */
    public CurrentPageResult<ArticleBrief> getByTagId(Object current, Object size, Integer tagId) {
        List<Integer> articleIds = articleIdsByTagId(tagId);
        return pageBrief(current, size, wrapper -> wrapper.in(ArticleEntity::getId, articleIds), articleIds.isEmpty());
    }

    /** 按分类分页 */
    public CurrentPageResult<ArticleBrief> getByCategoryId(Object current, Object size, Integer categoryId) {
        return pageBrief(current, size,
                wrapper -> wrapper.eq(ArticleEntity::getCategoryId, categoryId), false);
    }

    private CurrentPageResult<ArticleBrief> pageBrief(Object current, Object size,
                                                      java.util.function.Consumer<LambdaQueryWrapper<ArticleEntity>> filter,
                                                      boolean empty) {
        int pageNo = Math.max(Num.toInt(current, 1), 1);
        int pageSize = Num.toInt(size, 10);
        if (pageSize <= 0) {
            pageSize = 10;
        }
        if (empty) {
            return CurrentPageResult.of(current, size, 0, List.of());
        }

        LambdaQueryWrapper<ArticleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(ArticleEntity::getId, ArticleEntity::getArticleTitle,
                        ArticleEntity::getArticleCover, ArticleEntity::getCreatedAt)
                .eq(ArticleEntity::getStatus, 1);
        filter.accept(wrapper);
        wrapper.orderByDesc(ArticleEntity::getCreatedAt);

        Page<ArticleEntity> page = articleMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<ArticleBrief> list = page.getRecords().stream().map(entity -> {
            ArticleBrief brief = new ArticleBrief();
            BeanUtils.copyProperties(entity, brief);
            return brief;
        }).toList();
        return CurrentPageResult.of(
                current != null ? current : pageNo,
                size != null ? size : pageSize,
                page.getTotal(),
                list);
    }

    /** 文章详情：访问次数 +1 后返回（与原实现一致，响应里就是加过之后的值） */
    public ArticleDetail getDetail(Integer id) {
        ArticleEntity entity = articleMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        articleMapper.incrementViewTimes(id);
        entity.setViewTimes((entity.getViewTimes() == null ? 0 : entity.getViewTimes()) + 1);

        ArticleDetail detail = new ArticleDetail();
        BeanUtils.copyProperties(entity, detail);

        detail.setTagIdList(articleMapper.selectTagIds(id));
        detail.setTagNameList(articleMapper.selectTagsByArticleId(id).stream()
                .map(TagBrief::getTagName).toList());

        if (entity.getCategoryId() != null) {
            CategoryEntity category = categoryMapper.selectById(entity.getCategoryId());
            if (category != null) {
                detail.setCategoryName(category.getCategoryName());
            }
        }
        if (entity.getAuthorId() != null) {
            detail.setAuthorName(userMapper.selectNickNameById(entity.getAuthorId()));
        }
        return detail;
    }

    /** 上一/下一篇文章 + 最多 6 篇同标签推荐 */
    public RecommendResult getRecommend(Integer articleId) {
        RecommendResult result = new RecommendResult();

        ArticleEntity previous = articleMapper.selectOne(new LambdaQueryWrapper<ArticleEntity>()
                .select(ArticleEntity::getId, ArticleEntity::getArticleTitle, ArticleEntity::getArticleCover)
                .lt(ArticleEntity::getId, articleId)
                .eq(ArticleEntity::getStatus, 1)
                .orderByDesc(ArticleEntity::getId)
                .last("LIMIT 1"));
        ArticleEntity next = articleMapper.selectOne(new LambdaQueryWrapper<ArticleEntity>()
                .select(ArticleEntity::getId, ArticleEntity::getArticleTitle, ArticleEntity::getArticleCover)
                .gt(ArticleEntity::getId, articleId)
                .eq(ArticleEntity::getStatus, 1)
                .orderByAsc(ArticleEntity::getId)
                .last("LIMIT 1"));

        // 上下文不存在时取当前文章（原实现行为）
        ArticleEntity self = null;
        if (previous == null || next == null) {
            self = articleMapper.selectOne(new LambdaQueryWrapper<ArticleEntity>()
                    .select(ArticleEntity::getId, ArticleEntity::getArticleTitle, ArticleEntity::getArticleCover)
                    .eq(ArticleEntity::getId, articleId)
                    .last("LIMIT 1"));
        }
        result.setPrevious(toNeighbor(previous != null ? previous : self));
        result.setNext(toNeighbor(next != null ? next : self));

        List<Integer> tagIds = articleMapper.selectTagIds(articleId);
        List<ArticleBrief> recommend = new ArrayList<>();
        if (!tagIds.isEmpty()) {
            List<Integer> articleIds = articleIdsByTagIds(tagIds);
            if (!articleIds.isEmpty()) {
                recommend = articleMapper.selectList(new LambdaQueryWrapper<ArticleEntity>()
                                .select(ArticleEntity::getId, ArticleEntity::getArticleTitle,
                                        ArticleEntity::getArticleCover, ArticleEntity::getCreatedAt)
                                .in(ArticleEntity::getId, articleIds)
                                .eq(ArticleEntity::getStatus, 1)
                                .orderByDesc(ArticleEntity::getCreatedAt)
                                .last("LIMIT 6"))
                        .stream().map(entity -> {
                            ArticleBrief brief = new ArticleBrief();
                            BeanUtils.copyProperties(entity, brief);
                            return brief;
                        }).toList();
            }
        }
        result.setRecommend(recommend);
        return result;
    }

    private RecommendResult.ArticleNeighbor toNeighbor(ArticleEntity entity) {
        if (entity == null) {
            return null;
        }
        RecommendResult.ArticleNeighbor neighbor = new RecommendResult.ArticleNeighbor();
        neighbor.setId(entity.getId());
        neighbor.setArticleTitle(entity.getArticleTitle());
        neighbor.setArticleCover(entity.getArticleCover());
        return neighbor;
    }

    /** 全文搜索：命中片段从关键词位置起截 关键词长度+12 个字符 */
    public List<SearchResultItem> search(String content) {
        List<ArticleEntity> rows = articleMapper.searchByContent(content);
        List<SearchResultItem> result = new ArrayList<>();
        for (ArticleEntity row : rows) {
            String text = row.getArticleContent() == null ? "" : row.getArticleContent();
            int index = text.indexOf(content);
            if (index < 0) {
                index = 0;
            }
            int end = Math.min(text.length(), index + content.length() + 12);
            SearchResultItem item = new SearchResultItem();
            item.setId(row.getId());
            item.setArticleTitle(row.getArticleTitle());
            item.setArticleContent(text.substring(Math.min(index, end), end));
            result.add(item);
        }
        return result;
    }

    public List<HotArticleItem> getHot() {
        return articleMapper.selectHot();
    }

    /** 标题查重：返回 true 表示“已存在（且不是自己）” */
    public boolean titleExists(Object id, String title) {
        List<ArticleEntity> list = articleMapper.selectList(new LambdaQueryWrapper<ArticleEntity>()
                .select(ArticleEntity::getId)
                .eq(ArticleEntity::getArticleTitle, title)
                .last("LIMIT 1"));
        if (list.isEmpty()) {
            return false;
        }
        Integer selfId = Num.toInteger(id);
        if (selfId == null || selfId == 0) {
            return true;
        }
        return !Objects.equals(list.get(0).getId(), selfId);
    }

    /** 后台分页：默认只查公开+私密（status in 1,2），排除草稿 */
    public CurrentPageResult<ArticleListItem> getAdminList(ArticleQuery query) {
        Object rawCurrent = query.getCurrent();
        Object rawSize = query.getSize();
        int pageNo = Math.max(Num.toInt(rawCurrent, 1), 1);
        int pageSize = Num.toInt(rawSize, 10);
        if (pageSize <= 0) {
            pageSize = 10;
        }

        LambdaQueryWrapper<ArticleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(ArticleEntity::getId, ArticleEntity::getArticleTitle, ArticleEntity::getAuthorId,
                ArticleEntity::getCategoryId, ArticleEntity::getArticleDescription, ArticleEntity::getArticleCover,
                ArticleEntity::getIsTop, ArticleEntity::getOrder, ArticleEntity::getStatus,
                ArticleEntity::getType, ArticleEntity::getCreatedAt, ArticleEntity::getUpdatedAt,
                ArticleEntity::getViewTimes, ArticleEntity::getThumbsUpTimes, ArticleEntity::getReadingDuration);

        if (query.getArticleTitle() != null && !query.getArticleTitle().isBlank()) {
            wrapper.like(ArticleEntity::getArticleTitle, query.getArticleTitle());
        }
        Integer isTop = Num.toInteger(query.getIsTop());
        if (isTop != null && isTop != 0) {
            wrapper.eq(ArticleEntity::getIsTop, isTop);
        }
        Integer status = Num.toInteger(query.getStatus());
        if (status != null && status != 0) {
            wrapper.eq(ArticleEntity::getStatus, status);
        } else {
            wrapper.in(ArticleEntity::getStatus, List.of(1, 2));
        }
        Integer categoryId = Num.toInteger(query.getCategoryId());
        if (categoryId != null && categoryId != 0) {
            wrapper.eq(ArticleEntity::getCategoryId, categoryId);
        }
        LocalDateTime[] createTime = parseRange(query.getCreateTime());
        if (createTime != null) {
            wrapper.between(ArticleEntity::getCreatedAt, createTime[0], createTime[1]);
        }
        Integer tagId = Num.toInteger(query.getTagId());
        if (tagId != null && tagId != 0) {
            List<Integer> articleIds = articleIdsByTagId(tagId);
            if (articleIds.isEmpty()) {
                // 原实现这里会“不过滤”变成全量，属于 bug，这里按语义返回空
                return CurrentPageResult.of(rawCurrent, rawSize, 0, List.of());
            }
            wrapper.in(ArticleEntity::getId, articleIds);
        }
        wrapper.orderByDesc(ArticleEntity::getCreatedAt);

        Page<ArticleEntity> page = articleMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<ArticleListItem> list = page.getRecords().stream().map(entity -> {
            ArticleListItem item = new ArticleListItem();
            BeanUtils.copyProperties(entity, item);
            return item;
        }).toList();
        enrichListItems(list);

        return CurrentPageResult.of(
                rawCurrent != null ? rawCurrent : pageNo,
                rawSize != null ? rawSize : pageSize,
                page.getTotal(),
                list);
    }

    // ==================== 写 ====================

    public ArticleCreateResult create(ArticleForm form) {
        // 截到秒：MySQL DATETIME 会把毫秒四舍五入，内存值与落库值会差 1 秒
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Integer categoryId = resolveCategory(form.getCategory());

        ArticleEntity entity = new ArticleEntity();
        entity.setArticleTitle(form.getArticleTitle());
        entity.setAuthorId(Num.toInteger(form.getAuthorId()));
        entity.setCategoryId(categoryId);
        entity.setArticleDescription(form.getArticleDescription());
        entity.setArticleContent(form.getArticleContent());
        entity.setArticleCover(form.getArticleCover());
        entity.setIsTop(Num.toInteger(form.getIsTop()));
        entity.setOrder(Num.toInteger(form.getOrder()));
        entity.setStatus(Num.toInteger(form.getStatus()));
        entity.setType(Num.toInteger(form.getType()));
        entity.setOriginUrl(form.getOriginUrl());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        articleMapper.insert(entity);

        List<ArticleTagLink> links = saveArticleTags(entity.getId(), form.getTagList(), now);
        // 重新查一次，把数据库默认值（view_times 等）带出来，行为对齐 Sequelize 的 dataValues
        ArticleEntity created = articleMapper.selectById(entity.getId());

        ArticleCreateResult result = new ArticleCreateResult();
        result.setArticle(created);
        result.setArticleTagList(links);
        return result;
    }

    public ArticleUpdateResult update(ArticleForm form) {
        Integer id = Num.toInteger(form.getId());
        // 截到秒：MySQL DATETIME 会把毫秒四舍五入，内存值与落库值会差 1 秒
        LocalDateTime now = LocalDateTime.now().withNano(0);

        // 先删旧关联
        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTagEntity>()
                .eq(ArticleTagEntity::getArticleId, id));
        Integer categoryId = resolveCategory(form.getCategory());

        ArticleEntity entity = new ArticleEntity();
        entity.setId(id);
        entity.setArticleTitle(form.getArticleTitle());
        entity.setAuthorId(Num.toInteger(form.getAuthorId()));
        entity.setCategoryId(categoryId);
        entity.setArticleDescription(form.getArticleDescription());
        entity.setArticleContent(form.getArticleContent());
        entity.setArticleCover(form.getArticleCover());
        entity.setIsTop(Num.toInteger(form.getIsTop()));
        entity.setOrder(Num.toInteger(form.getOrder()));
        entity.setStatus(Num.toInteger(form.getStatus()));
        entity.setType(Num.toInteger(form.getType()));
        entity.setOriginUrl(form.getOriginUrl());
        entity.setUpdatedAt(now);
        int affected = articleMapper.updateById(entity);

        List<ArticleTagLink> links = saveArticleTags(id, form.getTagList(), now);

        ArticleUpdateResult result = new ArticleUpdateResult();
        result.setRes(affected > 0);
        result.setNewArticleTagList(links);
        return result;
    }

    public boolean updateTop(Integer id, Integer isTop) {
        ArticleEntity entity = new ArticleEntity();
        entity.setId(id);
        entity.setIsTop(isTop);
        return articleMapper.updateById(entity) > 0;
    }

    /**
     * 删除：status=3 时永久删除（并删标签关联），否则改成草稿。
     */
    public boolean delete(Integer id, int status) {
        if (status == 3) {
            int affected = articleMapper.deleteById(id);
            articleTagMapper.delete(new LambdaQueryWrapper<ArticleTagEntity>()
                    .eq(ArticleTagEntity::getArticleId, id));
            return affected > 0;
        }
        ArticleEntity entity = new ArticleEntity();
        entity.setId(id);
        entity.setStatus(3);
        return articleMapper.updateById(entity) > 0;
    }

    public boolean revert(Integer id) {
        ArticleEntity entity = new ArticleEntity();
        entity.setId(id);
        entity.setStatus(1);
        return articleMapper.updateById(entity) > 0;
    }

    /** 切换公开/私密：请求里的 status 是“当前状态”，2 -> 公开(1)，其它 -> 私密(2) */
    public boolean togglePublic(Integer id, int currentStatus) {
        ArticleEntity entity = new ArticleEntity();
        entity.setId(id);
        entity.setStatus(currentStatus == 2 ? 1 : 2);
        return articleMapper.updateById(entity) > 0;
    }

    public boolean like(Integer id) {
        return articleMapper.incrementThumbsUp(id) > 0;
    }

    public boolean cancelLike(Integer id) {
        return articleMapper.decrementThumbsUp(id) > 0;
    }

    public boolean addReadingDuration(Integer id, double duration) {
        return articleMapper.increaseReadingDuration(id, duration) > 0;
    }

    /** 历史封面域名迁移，返回受影响行数 */
    public int replaceCoverDomain() {
        return articleMapper.replaceCoverDomain();
    }

    // ==================== 内部工具 ====================

    /** 分类：有 id 用 id；否则按名称查，没有就新建 */
    private Integer resolveCategory(com.mrzym.blog.dto.ArticleCategoryForm category) {
        if (category == null) {
            return null;
        }
        Integer id = Num.toInteger(category.getId());
        if (id != null && id != 0) {
            return id;
        }
        String name = category.getCategoryName();
        if (name == null) {
            return null;
        }
        List<CategoryEntity> existing = categoryMapper.selectList(new LambdaQueryWrapper<CategoryEntity>()
                .eq(CategoryEntity::getCategoryName, name)
                .last("LIMIT 1"));
        if (!existing.isEmpty()) {
            return existing.get(0).getId();
        }
        // 截到秒：MySQL DATETIME 会把毫秒四舍五入，内存值与落库值会差 1 秒
        LocalDateTime now = LocalDateTime.now().withNano(0);
        CategoryEntity created = new CategoryEntity();
        created.setCategoryName(name);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        categoryMapper.insert(created);
        return created.getId();
    }

    private List<ArticleTagLink> saveArticleTags(Integer articleId, List<ArticleTagForm> tagList, LocalDateTime now) {
        if (tagList == null || tagList.isEmpty()) {
            return null;
        }
        List<ArticleTagLink> result = new ArrayList<>();
        for (ArticleTagForm tag : tagList) {
            Integer tagId = Num.toInteger(tag.getId());
            if (tagId == null || tagId == 0) {
                tagId = findOrCreateTag(tag.getTagName(), now);
            }
            ArticleTagEntity link = new ArticleTagEntity();
            link.setArticleId(articleId);
            link.setTagId(tagId);
            link.setCreatedAt(now);
            link.setUpdatedAt(now);
            articleTagMapper.insert(link);

            ArticleTagLink item = new ArticleTagLink();
            item.setId(link.getId());
            item.setArticleId(articleId);
            item.setTagId(tagId);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            result.add(item);
        }
        return result.isEmpty() ? null : result;
    }

    private Integer findOrCreateTag(String tagName, LocalDateTime now) {
        if (tagName == null) {
            return null;
        }
        List<TagEntity> existing = tagMapper.selectList(new LambdaQueryWrapper<TagEntity>()
                .eq(TagEntity::getTagName, tagName)
                .last("LIMIT 1"));
        if (!existing.isEmpty()) {
            return existing.get(0).getId();
        }
        TagEntity created = new TagEntity();
        created.setTagName(tagName);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        tagMapper.insert(created);
        return created.getId();
    }

    private List<Integer> articleIdsByTagId(Integer tagId) {
        return articleIdsByTagIdInternal(tagId);
    }

    /** 按标签查文章 id（去重，保持关联表顺序） */
    private List<Integer> articleIdsByTagIdInternal(Integer tagId) {
        List<ArticleTagEntity> links = articleTagMapper.selectList(new LambdaQueryWrapper<ArticleTagEntity>()
                .select(ArticleTagEntity::getArticleId)
                .eq(ArticleTagEntity::getTagId, tagId));
        return links.stream().map(ArticleTagEntity::getArticleId)
                .filter(Objects::nonNull).distinct().toList();
    }

    private List<Integer> articleIdsByTagIds(List<Integer> tagIds) {
        List<ArticleTagEntity> links = articleTagMapper.selectList(new LambdaQueryWrapper<ArticleTagEntity>()
                .select(ArticleTagEntity::getArticleId)
                .in(ArticleTagEntity::getTagId, tagIds));
        return links.stream().map(ArticleTagEntity::getArticleId)
                .filter(Objects::nonNull).distinct().toList();
    }

    /** 列表补 categoryName / tagNameList（批量 IN，避免 N+1） */
    private void enrichListItems(List<ArticleListItem> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Set<Integer> categoryIds = new LinkedHashSet<>();
        List<Integer> articleIds = new ArrayList<>();
        for (ArticleListItem item : list) {
            if (item.getCategoryId() != null) {
                categoryIds.add(item.getCategoryId());
            }
            articleIds.add(item.getId());
        }

        Map<Integer, String> categoryNames = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            for (CategoryBrief brief : articleMapper.selectCategoryNames(new ArrayList<>(categoryIds))) {
                categoryNames.put(brief.getId(), brief.getCategoryName());
            }
        }
        Map<Integer, List<String>> tagNames = new HashMap<>();
        for (ArticleTagRow row : articleMapper.selectTagNames(articleIds)) {
            tagNames.computeIfAbsent(row.getArticleId(), k -> new ArrayList<>()).add(row.getTagName());
        }
        for (ArticleListItem item : list) {
            item.setCategoryName(categoryNames.get(item.getCategoryId()));
            item.setTagNameList(tagNames.getOrDefault(item.getId(), List.of()));
        }
    }

    private LocalDateTime[] parseRange(Object raw) {
        if (!(raw instanceof List<?> list) || list.size() != 2) {
            return null;
        }
        LocalDateTime start = parseDateTime(list.get(0));
        LocalDateTime end = parseDateTime(list.get(1));
        if (start == null || end == null) {
            return null;
        }
        return new LocalDateTime[]{start, end};
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, DATE_TIME);
        } catch (Exception e) {
            log.warn("无法解析文章查询时间: {}", text);
            return null;
        }
    }
}
