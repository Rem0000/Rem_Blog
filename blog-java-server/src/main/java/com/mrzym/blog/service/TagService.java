package com.mrzym.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.common.PageResult;
import com.mrzym.blog.entity.TagEntity;
import com.mrzym.blog.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagMapper tagMapper;

    public TagEntity createTag(String tagName) {
        TagEntity entity = new TagEntity();
        entity.setTagName(tagName);
        tagMapper.insert(entity);
        return entity;
    }

    public boolean updateTag(Integer id, String tagName) {
        TagEntity entity = new TagEntity();
        entity.setId(id);
        entity.setTagName(tagName);
        return tagMapper.updateById(entity) > 0;
    }

    public int deleteTags(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return tagMapper.deleteBatchIds(ids);
    }

    public TagEntity getOneTag(Integer id, String tagName) {
        LambdaQueryWrapper<TagEntity> wrapper = new LambdaQueryWrapper<>();
        if (id != null) {
            wrapper.eq(TagEntity::getId, id);
        }
        if (tagName != null && !tagName.isEmpty()) {
            wrapper.eq(TagEntity::getTagName, tagName);
        }
        List<TagEntity> list = tagMapper.selectList(wrapper.last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    public PageResult<TagEntity> getTagList(Object page, Object pageSize, String tagName) {
        int pageNo = Num.toInt(page, 1);
        int size = Num.toInt(pageSize, 10);
        LambdaQueryWrapper<TagEntity> wrapper = new LambdaQueryWrapper<>();
        if (tagName != null && !tagName.isEmpty()) {
            wrapper.like(TagEntity::getTagName, tagName);
        }
        IPage<TagEntity> result = tagMapper.selectPage(new Page<>(pageNo, size), wrapper);
        return PageResult.of(page, pageSize, result.getTotal(), result.getRecords());
    }

    /** 供文章模块反查用：按 id 列表取标签 */
    public List<TagEntity> getTagsByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return tagMapper.selectList(new LambdaQueryWrapper<TagEntity>()
                .select(TagEntity::getId, TagEntity::getTagName)
                .in(TagEntity::getId, ids));
    }

    public List<Map<String, Object>> getTagDictionary() {
        List<TagEntity> list = tagMapper.selectList(
                new LambdaQueryWrapper<TagEntity>()
                        .select(TagEntity::getId, TagEntity::getTagName));
        return list.stream().map(e -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("tag_name", e.getTagName());
            return item;
        }).collect(Collectors.toList());
    }

    public long getTagCount() {
        return tagMapper.selectCount(null);
    }
}
