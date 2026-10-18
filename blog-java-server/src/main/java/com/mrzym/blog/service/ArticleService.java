package com.mrzym.blog.service;

import com.mrzym.blog.dto.ArticleListItem;
import com.mrzym.blog.dto.ArticleListResult;
import com.mrzym.blog.dto.ArticleTagRow;
import com.mrzym.blog.dto.CategoryBrief;
import com.mrzym.blog.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleMapper articleMapper;

    /**
     * 首页文章列表。对齐原实现 blogHomeGetArticleList：
     * 先分页查文章，再补 categoryName 与 tagNameList（避免 N+1，这里用两次 IN 查询批量取）。
     */
    public ArticleListResult getHomeList(int current, int size) {
        int safeSize = size <= 0 ? 10 : size;
        int offset = Math.max(current - 1, 0) * safeSize;

        long total = articleMapper.countPublished();
        List<ArticleListItem> list = articleMapper.selectHomePage(offset, safeSize);
        if (list.isEmpty()) {
            return ArticleListResult.of(current, size, total, list);
        }

        Set<Integer> categoryIds = new LinkedHashSet<>();
        Set<Integer> articleIds = new LinkedHashSet<>();
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
        for (ArticleTagRow row : articleMapper.selectTagNames(new ArrayList<>(articleIds))) {
            tagNames.computeIfAbsent(row.getArticleId(), k -> new ArrayList<>()).add(row.getTagName());
        }

        for (ArticleListItem item : list) {
            item.setCategoryName(categoryNames.get(item.getCategoryId()));
            item.setTagNameList(tagNames.getOrDefault(item.getId(), List.of()));
        }
        return ArticleListResult.of(current, size, total, list);
    }
}
