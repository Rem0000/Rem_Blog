package com.mrzym.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mrzym.blog.common.CurrentPageResult;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.dto.LinkForm;
import com.mrzym.blog.dto.LinkQuery;
import com.mrzym.blog.entity.LinkEntity;
import com.mrzym.blog.mapper.LinkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 友链服务，对齐原实现 service/links/index.js。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LinkMapper linkMapper;

    /**
     * 请求体带不带 id —— 与原实现 `if (id)` 的真值语义一致：
     * null / "" / 0 都算新增。
     */
    public boolean isUpdate(LinkForm form) {
        Object raw = form.getId();
        if (raw == null) {
            return false;
        }
        String value = String.valueOf(raw).trim();
        return !value.isEmpty() && !"0".equals(value);
    }

    /**
     * 新增/修改友链。原实现：
     *  - 有 id：update 四个字段 + status（Sequelize 自动更新 updatedAt），返回值恒为 true；
     *  - 无 id：create，status 强制 1，user_id 原样落库。
     */
    public boolean addOrUpdate(LinkForm form) {
        LocalDateTime now = LocalDateTime.now().withNano(0);

        if (isUpdate(form)) {
            Integer id = Num.toInteger(form.getId());
            if (id == null) {
                // 原实现 update where id='非数字' 影响 0 行，但返回值仍是数组（真值） -> true
                return true;
            }
            LinkEntity patch = new LinkEntity();
            patch.setId(id);
            patch.setSiteName(form.getSiteName());
            patch.setSiteDesc(form.getSiteDesc());
            patch.setSiteAvatar(form.getSiteAvatar());
            patch.setUrl(form.getUrl());
            Integer status = Num.toInteger(form.getStatus());
            if (status != null) {
                patch.setStatus(status);
            }
            patch.setUpdatedAt(now);
            linkMapper.updateById(patch);
            return true;
        }

        LinkEntity entity = new LinkEntity();
        entity.setSiteName(form.getSiteName());
        entity.setSiteDesc(form.getSiteDesc());
        entity.setSiteAvatar(form.getSiteAvatar());
        entity.setUrl(form.getUrl());
        // 原实现写死 status: 1，忽略请求体
        entity.setStatus(1);
        entity.setUserId(form.getUserId() == null ? null : String.valueOf(form.getUserId()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        linkMapper.insert(entity);
        return true;
    }

    /**
     * 批量删除。原实现 `return res ? res : null`：0 条时返回 null。
     */
    public Integer deleteLinks(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        int affected = linkMapper.deleteBatchIds(ids);
        return affected > 0 ? affected : null;
    }

    /**
     * 批量审核（status -> 2）。原实现返回 Sequelize update 的数组，即 [affectedCount]。
     */
    public int approveLinks(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now().withNano(0);
        return linkMapper.update(null, new LambdaUpdateWrapper<LinkEntity>()
                .set(LinkEntity::getStatus, 2)
                .set(LinkEntity::getUpdatedAt, now)
                .in(LinkEntity::getId, ids));
    }

    /**
     * 分页查询。入参/出参都是 current / size（现网契约）。
     */
    public CurrentPageResult<LinkEntity> getLinksList(LinkQuery query) {
        // current/size 优先；没有时兼容 page/pageSize 写法
        Object rawCurrent = query.getCurrent() != null ? query.getCurrent() : query.getPage();
        Object rawSize = query.getSize() != null ? query.getSize() : query.getPageSize();

        int current = Math.max(Num.toInt(rawCurrent, 1), 1);
        int size = Num.toInt(rawSize, 10);
        if (size <= 0) {
            size = 10;
        }

        LambdaQueryWrapper<LinkEntity> wrapper = new LambdaQueryWrapper<>();
        if (query.getSiteName() != null && !query.getSiteName().isBlank()) {
            wrapper.like(LinkEntity::getSiteName, query.getSiteName());
        }
        Integer status = Num.toInteger(query.getStatus());
        // 原实现是 if (status)：0 / "" / null 都不加过滤（JS 真值语义）
        if (status != null && status != 0) {
            wrapper.eq(LinkEntity::getStatus, status);
        }
        LocalDateTime[] range = parseTimeRange(query.getTime());
        if (range != null) {
            wrapper.between(LinkEntity::getCreatedAt, range[0], range[1]);
        }
        wrapper.orderByAsc(LinkEntity::getCreatedAt).orderByAsc(LinkEntity::getId);

        Page<LinkEntity> page = linkMapper.selectPage(new Page<>(current, size), wrapper);
        return CurrentPageResult.of(
                rawCurrent != null ? rawCurrent : current,
                rawSize != null ? rawSize : size,
                page.getTotal(),
                page.getRecords());
    }

    /**
     * time 形如 ["2024-01-01 00:00:00","2024-12-31 23:59:59"]；
     * 空串/空数组/长度不为 2 都当作不过滤（原实现空数组会拼出非法 SQL，这里不回放这个坑）。
     */
    private LocalDateTime[] parseTimeRange(Object raw) {
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
            log.warn("无法解析友链查询时间: {}", text);
            return null;
        }
    }
}
