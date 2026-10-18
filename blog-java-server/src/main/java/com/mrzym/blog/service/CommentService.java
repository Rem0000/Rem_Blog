package com.mrzym.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mrzym.blog.common.IpUtil;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.common.SensitiveWordFilter;
import com.mrzym.blog.dto.CommentAddForm;
import com.mrzym.blog.dto.CommentBackQuery;
import com.mrzym.blog.dto.CommentFrontQuery;
import com.mrzym.blog.dto.CommentPageResult;
import com.mrzym.blog.dto.CommentView;
import com.mrzym.blog.entity.CommentEntity;
import com.mrzym.blog.entity.UserEntity;
import com.mrzym.blog.mapper.CommentMapper;
import com.mrzym.blog.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 评论，对齐原实现 service/comment。
 *
 * 注意：原实现 controller 里把 errorCode 写成了 ERRORCODE.CATEGORY（100004），
 * 所以评论相关错误返回的 code 是 100004 而不是 100015，这里照抄。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final NotifyService notifyService;
    private final LikeService likeService;

    /** 新增父级评论 */
    public CommentEntity add(CommentAddForm form, String ip, String content) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        CommentEntity entity = new CommentEntity();
        entity.setType(Num.toInteger(form.getType()));
        entity.setForId(Num.toInteger(form.getForId()));
        entity.setFromId(Num.toInteger(form.getFromId()));
        entity.setFromName(form.getFromName());
        entity.setFromAvatar(form.getFromAvatar());
        entity.setContent(content);
        entity.setIp(ip);
        entity.setThumbsUp(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        commentMapper.insert(entity);
        return entity;
    }

    /** 回复评论 */
    public CommentEntity apply(CommentAddForm form, String ip, String content) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        CommentEntity entity = new CommentEntity();
        entity.setParentId(Num.toInteger(form.getParentId()));
        entity.setType(Num.toInteger(form.getType()));
        entity.setForId(Num.toInteger(form.getForId()));
        entity.setFromId(Num.toInteger(form.getFromId()));
        entity.setFromName(form.getFromName());
        entity.setFromAvatar(form.getFromAvatar());
        entity.setToId(Num.toInteger(form.getToId()));
        entity.setToName(form.getToName());
        entity.setToAvatar(form.getToAvatar());
        entity.setContent(content);
        entity.setIp(ip);
        entity.setThumbsUp(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        commentMapper.insert(entity);
        return entity;
    }

    public boolean like(Integer id) {
        return commentMapper.increaseThumbsUp(id) > 0;
    }

    public boolean cancelLike(Integer id) {
        return commentMapper.decreaseThumbsUp(id) > 0;
    }

    /** parent_id > 0 只删本条；否则连同子评论一起删。返回删除条数（0 -> null） */
    public Integer delete(Integer id, Integer parentId) {
        int affected = commentMapper.deleteById(id);
        if (parentId == null || parentId <= 0) {
            commentMapper.delete(new LambdaQueryWrapper<CommentEntity>()
                    .eq(CommentEntity::getParentId, id));
        }
        return affected > 0 ? affected : null;
    }

    /** 后台分页 */
    public CommentPageResult<CommentView> backList(CommentBackQuery query) {
        Object rawCurrent = query.getCurrent();
        Object rawSize = query.getSize();
        int pageNo = Math.max(Num.toInt(rawCurrent, 1), 1);
        int pageSize = Num.toInt(rawSize, 10);
        if (pageSize <= 0) {
            pageSize = 10;
        }

        LambdaQueryWrapper<CommentEntity> wrapper = new LambdaQueryWrapper<>();
        if (query.getContent() != null && !query.getContent().isBlank()) {
            wrapper.like(CommentEntity::getContent, query.getContent());
        }
        // 原实现这里写的是不存在的 name 列（传了就 500），这里按语义修正
        if (query.getFromName() != null && !query.getFromName().isBlank()) {
            wrapper.like(CommentEntity::getFromName, query.getFromName());
        }
        if (query.getToName() != null && !query.getToName().isBlank()) {
            wrapper.like(CommentEntity::getToName, query.getToName());
        }
        LocalDateTime[] range = parseRange(query.getTime());
        if (range != null) {
            wrapper.between(CommentEntity::getCreatedAt, range[0], range[1]);
        }
        wrapper.orderByDesc(CommentEntity::getCreatedAt);

        Page<CommentEntity> page = commentMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<CommentView> list = toViews(page.getRecords());
        return CommentPageResult.of(
                rawCurrent != null ? rawCurrent : pageNo,
                rawSize != null ? rawSize : pageSize,
                page.getTotal(),
                list);
    }

    /** 前台父级评论 */
    public CommentPageResult<CommentView> frontParents(CommentFrontQuery query, String ip) {
        Object rawCurrent = query.getCurrent();
        Object rawSize = query.getSize();
        int pageNo = Math.max(Num.toInt(rawCurrent, 1), 1);
        int pageSize = Num.toInt(rawSize, 10);
        if (pageSize <= 0) {
            pageSize = 10;
        }

        LambdaQueryWrapper<CommentEntity> wrapper = new LambdaQueryWrapper<>();
        Integer type = Num.toInteger(query.getType());
        if (type != null && type != 0) {
            wrapper.eq(CommentEntity::getType, type);
        }
        Integer forId = Num.toInteger(query.getForId());
        if (forId != null && forId != 0) {
            wrapper.eq(CommentEntity::getForId, forId);
        }
        wrapper.isNull(CommentEntity::getParentId);
        if ("new".equals(query.getOrder())) {
            wrapper.orderByDesc(CommentEntity::getCreatedAt);
        } else {
            wrapper.orderByDesc(CommentEntity::getThumbsUp);
        }

        Page<CommentEntity> page = commentMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<CommentView> list = toViews(page.getRecords());
        fillLiked(list, query.getUserId(), ip);
        return CommentPageResult.of(
                rawCurrent != null ? rawCurrent : pageNo,
                rawSize != null ? rawSize : pageSize,
                page.getTotal(),
                list);
    }

    /** 前台子评论：按 parent_id 查，创建时间升序 */
    public CommentPageResult<CommentView> frontChildren(CommentFrontQuery query, String ip) {
        Object rawCurrent = query.getCurrent();
        Object rawSize = query.getSize();
        int pageNo = Math.max(Num.toInt(rawCurrent, 1), 1);
        int pageSize = Num.toInt(rawSize, 10);
        if (pageSize <= 0) {
            pageSize = 10;
        }

        LambdaQueryWrapper<CommentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommentEntity::getType, Num.toInteger(query.getType()));
        wrapper.eq(CommentEntity::getForId, Num.toInteger(query.getForId()));
        wrapper.eq(CommentEntity::getParentId, Num.toInteger(query.getParentId()));
        wrapper.orderByAsc(CommentEntity::getCreatedAt);

        Page<CommentEntity> page = commentMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<CommentView> list = toViews(page.getRecords());
        fillLiked(list, query.getUserId(), ip);
        return CommentPageResult.of(
                rawCurrent != null ? rawCurrent : pageNo,
                rawSize != null ? rawSize : pageSize,
                page.getTotal(),
                list);
    }

    /** 评论总数（父 + 子），按 for_id + type */
    public long total(Object forId, Object type) {
        LambdaQueryWrapper<CommentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommentEntity::getForId, Num.toInteger(forId));
        wrapper.eq(CommentEntity::getType, Num.toInteger(type));
        return commentMapper.selectCount(wrapper);
    }

    // ==================== 组装 ====================

    private List<CommentView> toViews(List<CommentEntity> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Integer> userIds = new LinkedHashSet<>();
        for (CommentEntity row : rows) {
            if (row.getFromId() != null) {
                userIds.add(row.getFromId());
            }
            if (row.getToId() != null) {
                userIds.add(row.getToId());
            }
        }
        Map<Integer, UserEntity> users = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (UserEntity user : userMapper.selectBatchIds(userIds)) {
                users.put(user.getId(), user);
            }
        }

        List<CommentView> list = new ArrayList<>(rows.size());
        for (CommentEntity row : rows) {
            CommentView view = new CommentView();
            BeanUtils.copyProperties(row, view);
            view.setIpAddress(IpUtil.locate(row.getIp()));

            UserEntity from = users.get(row.getFromId());
            if (from != null) {
                // 原实现只覆盖头像 + 拼错的 form_name，不动 from_name
                view.setFromAvatar(from.getAvatar());
                view.setFormName(from.getNickName());
            }
            UserEntity to = users.get(row.getToId());
            if (to != null) {
                view.setToAvatar(to.getAvatar());
                view.setToName(to.getNickName());
            }
            list.add(view);
        }
        return list;
    }

    private void fillLiked(List<CommentView> list, Object userIdRaw, String ip) {
        Integer userId = Num.toInteger(userIdRaw);
        boolean byUser = userId != null && userId != 0;
        for (CommentView view : list) {
            boolean liked = byUser
                    ? likeService.isLikedByUser(view.getId(), 4, userId)
                    : likeService.isLikedByIp(view.getId(), 4, ip);
            view.setLiked(liked);
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
            log.warn("无法解析评论查询时间: {}", text);
            return null;
        }
    }

    /** 新增评论后的通知文案里，“文章/说说/留言”的映射 */
    public String typeName(Integer type) {
        if (type == null) {
            return "0";
        }
        return switch (type) {
            case 1 -> "文章";
            case 2 -> "说说";
            case 3 -> "留言";
            default -> "0";
        };
    }
}
