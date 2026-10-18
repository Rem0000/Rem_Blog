package com.mrzym.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.common.PageResult;
import com.mrzym.blog.entity.CategoryEntity;
import com.mrzym.blog.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public CategoryEntity createCategory(String categoryName) {
        CategoryEntity entity = new CategoryEntity();
        entity.setCategoryName(categoryName);
        categoryMapper.insert(entity);
        return entity;
    }

    public boolean updateCategory(Integer id, String categoryName) {
        CategoryEntity entity = new CategoryEntity();
        entity.setId(id);
        entity.setCategoryName(categoryName);
        return categoryMapper.updateById(entity) > 0;
    }

    public int deleteCategories(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return categoryMapper.deleteBatchIds(ids);
    }

    /** 按 id 或名称取一条，对应 getOneCategory */
    public CategoryEntity getOneCategory(Integer id, String categoryName) {
        LambdaQueryWrapper<CategoryEntity> wrapper = new LambdaQueryWrapper<>();
        if (id != null) {
            wrapper.eq(CategoryEntity::getId, id);
        }
        if (categoryName != null && !categoryName.isEmpty()) {
            wrapper.eq(CategoryEntity::getCategoryName, categoryName);
        }
        List<CategoryEntity> list = categoryMapper.selectList(wrapper.last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    public PageResult<CategoryEntity> getCategoryList(Object page, Object pageSize, String categoryName) {
        int pageNo = Num.toInt(page, 1);
        int size = Num.toInt(pageSize, 10);
        LambdaQueryWrapper<CategoryEntity> wrapper = new LambdaQueryWrapper<>();
        if (categoryName != null && !categoryName.isEmpty()) {
            wrapper.like(CategoryEntity::getCategoryName, categoryName);
        }
        IPage<CategoryEntity> result = categoryMapper.selectPage(new Page<>(pageNo, size), wrapper);
        return PageResult.of(page, pageSize, result.getTotal(), result.getRecords());
    }

    /** 字典：只回 id + category_name，不带时间字段（对齐原实现 attributes 限制） */
    public List<Map<String, Object>> getCategoryDictionary() {
        List<CategoryEntity> list = categoryMapper.selectList(
                new LambdaQueryWrapper<CategoryEntity>()
                        .select(CategoryEntity::getId, CategoryEntity::getCategoryName));
        return list.stream().map(e -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("category_name", e.getCategoryName());
            return item;
        }).collect(Collectors.toList());
    }

    public long getCategoryCount() {
        return categoryMapper.selectCount(null);
    }
}
