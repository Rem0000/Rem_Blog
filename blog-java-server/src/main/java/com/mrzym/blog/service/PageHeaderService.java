package com.mrzym.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mrzym.blog.dto.PageHeaderItem;
import com.mrzym.blog.entity.PageHeaderEntity;
import com.mrzym.blog.mapper.PageHeaderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PageHeaderService {

    private final PageHeaderMapper pageHeaderMapper;
    private final OssService ossService;

    public List<PageHeaderItem> getAll() {
        return pageHeaderMapper.selectAll();
    }

    /**
     * 新增/修改背景。原实现：有 id 走 update（route_name、bg_url，Sequelize 自动更新 updatedAt），
     * 无 id 走 create。返回值恒为 true。
     */
    public boolean addOrUpdate(Integer id, String routeName, String bgUrl) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        if (id != null && id != 0) {
            PageHeaderEntity patch = new PageHeaderEntity();
            patch.setId(id);
            patch.setRouteName(routeName);
            patch.setBgUrl(bgUrl);
            patch.setUpdatedAt(now);
            pageHeaderMapper.updateById(patch);
            return true;
        }
        PageHeaderEntity entity = new PageHeaderEntity();
        entity.setRouteName(routeName);
        entity.setBgUrl(bgUrl);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        pageHeaderMapper.insert(entity);
        return true;
    }

    /** 原实现 `return res ? res : null`：删 0 条返回 null */
    public Integer delete(Integer id) {
        if (id == null) {
            return null;
        }
        PageHeaderEntity old = pageHeaderMapper.selectById(id);
        int affected = pageHeaderMapper.deleteById(id);
        if (affected > 0) {
            deleteBgIfUnused(old == null ? null : old.getBgUrl());
        }
        return affected > 0 ? affected : null;
    }

    /** 背景图不再被任何页面引用时才删除 OSS 对象 */
    private void deleteBgIfUnused(String bgUrl) {
        if (bgUrl == null || bgUrl.isBlank()) {
            return;
        }
        Long refs = pageHeaderMapper.selectCount(new LambdaQueryWrapper<PageHeaderEntity>()
                .eq(PageHeaderEntity::getBgUrl, bgUrl));
        if (refs == null || refs == 0) {
            ossService.deleteByUrl(bgUrl);
        }
    }

    /** 按 route_name 查一条，对应原实现 getOneByPath */
    public PageHeaderEntity getOneByPath(String routeName) {
        if (routeName == null) {
            return null;
        }
        List<PageHeaderEntity> list = pageHeaderMapper.selectList(
                new LambdaQueryWrapper<PageHeaderEntity>()
                        .eq(PageHeaderEntity::getRouteName, routeName)
                        .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }
}
