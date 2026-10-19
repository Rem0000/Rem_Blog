package com.mrzym.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mrzym.blog.common.CurrentPageResult;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.dto.TalkDetail;
import com.mrzym.blog.dto.TalkForm;
import com.mrzym.blog.dto.TalkQuery;
import com.mrzym.blog.dto.TalkView;
import com.mrzym.blog.entity.TalkEntity;
import com.mrzym.blog.entity.TalkPhotoEntity;
import com.mrzym.blog.entity.UserEntity;
import com.mrzym.blog.mapper.TalkMapper;
import com.mrzym.blog.mapper.TalkPhotoMapper;
import com.mrzym.blog.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 说说，对齐原实现 service/talk + service/talk/talkPhoto。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TalkService {

    /** 详情接口的 ISO UTC 输出（原实现直接展开 Date，JSON 化后就是这种格式） */
    private static final DateTimeFormatter ISO_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private final TalkMapper talkMapper;
    private final TalkPhotoMapper talkPhotoMapper;
    private final UserMapper userMapper;
    private final LikeService likeService;
    private final OssService ossService;

    public Integer publish(TalkForm form) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        TalkEntity entity = new TalkEntity();
        entity.setContent(form.getContent());
        entity.setUserId(Num.toInteger(form.getUserId()));
        entity.setStatus(Num.toInteger(form.getStatus()));
        entity.setIsTop(Num.toInteger(form.getIsTop()));
        entity.setLikeTimes(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        talkMapper.insert(entity);

        savePhotos(entity.getId(), form.getTalkImgList(), now);
        return entity.getId();
    }

    /** 修改：先更新说说，再删旧图、插新图；返回受影响行数是否 > 0（原实现语义） */
    public boolean update(TalkForm form) {
        Integer id = Num.toInteger(form.getId());
        LocalDateTime now = LocalDateTime.now().withNano(0);
        List<String> oldUrls = photoUrls(id);

        TalkEntity patch = new TalkEntity();
        patch.setId(id);
        patch.setContent(form.getContent());
        patch.setUserId(Num.toInteger(form.getUserId()));
        patch.setStatus(Num.toInteger(form.getStatus()));
        patch.setIsTop(Num.toInteger(form.getIsTop()));
        patch.setUpdatedAt(now);
        int affected = talkMapper.updateById(patch);

        deletePhotos(id);
        savePhotos(id, form.getTalkImgList(), now);
        deleteUnusedPhotoUrls(oldUrls);
        return affected > 0;
    }

    /** 进回收站，返回受影响行数（原实现返回 Sequelize 的 [count]） */
    public int recycle(Integer id) {
        TalkEntity patch = new TalkEntity();
        patch.setId(id);
        patch.setStatus(3);
        patch.setUpdatedAt(LocalDateTime.now().withNano(0));
        return talkMapper.updateById(patch);
    }

    /** 永久删除 + 删图片关联，返回删除条数 */
    public int destroy(Integer id) {
        List<String> oldUrls = photoUrls(id);
        int affected = talkMapper.deleteById(id);
        deletePhotos(id);
        if (affected > 0) {
            deleteUnusedPhotoUrls(oldUrls);
        }
        return affected;
    }

    public boolean togglePublic(Integer id, Integer status) {
        TalkEntity patch = new TalkEntity();
        patch.setId(id);
        patch.setStatus(status);
        patch.setUpdatedAt(LocalDateTime.now().withNano(0));
        talkMapper.updateById(patch);
        return true;
    }

    public boolean toggleTop(Integer id, Integer isTop) {
        TalkEntity patch = new TalkEntity();
        patch.setId(id);
        patch.setIsTop(isTop);
        patch.setUpdatedAt(LocalDateTime.now().withNano(0));
        talkMapper.updateById(patch);
        return true;
    }

    public boolean revert(Integer id) {
        TalkEntity patch = new TalkEntity();
        patch.setId(id);
        patch.setStatus(1);
        patch.setUpdatedAt(LocalDateTime.now().withNano(0));
        talkMapper.updateById(patch);
        return true;
    }

    public boolean like(Integer id) {
        return talkMapper.increaseLike(id) > 0;
    }

    public boolean cancelLike(Integer id) {
        return talkMapper.decreaseLike(id) > 0;
    }

    /** 后台列表：status 有值才过滤 */
    public CurrentPageResult<TalkView> getTalkList(TalkQuery query) {
        LambdaQueryWrapper<TalkEntity> wrapper = new LambdaQueryWrapper<>();
        Integer status = Num.toInteger(query.getStatus());
        if (status != null && status != 0) {
            wrapper.eq(TalkEntity::getStatus, status);
        }
        return page(query, wrapper, false, null);
    }

    /** 前台列表：只出公开，附带 is_like */
    public CurrentPageResult<TalkView> blogGetTalkList(TalkQuery query, String ip) {
        LambdaQueryWrapper<TalkEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TalkEntity::getStatus, 1);
        return page(query, wrapper, true, ip);
    }

    private CurrentPageResult<TalkView> page(TalkQuery query, LambdaQueryWrapper<TalkEntity> wrapper,
                                             boolean withLike, String ip) {
        Object rawCurrent = query.getCurrent();
        Object rawSize = query.getSize();
        int pageNo = Math.max(Num.toInt(rawCurrent, 1), 1);
        int pageSize = Num.toInt(rawSize, 10);
        if (pageSize <= 0) {
            pageSize = 10;
        }
        wrapper.orderByAsc(TalkEntity::getIsTop).orderByDesc(TalkEntity::getCreatedAt);

        Page<TalkEntity> page = talkMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<TalkView> list = toViews(page.getRecords(), withLike,
                Num.toInteger(query.getUserId()), ip);
        return CurrentPageResult.of(
                rawCurrent != null ? rawCurrent : pageNo,
                rawSize != null ? rawSize : pageSize,
                page.getTotal(),
                list);
    }

    /** 详情：不带用户信息，talkImgList 恒存在；时间输出 ISO UTC */
    public TalkDetail getTalkById(Integer id) {
        TalkEntity entity = talkMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        TalkDetail detail = new TalkDetail();
        detail.setId(entity.getId());
        detail.setContent(entity.getContent());
        detail.setUserId(entity.getUserId());
        detail.setStatus(entity.getStatus());
        detail.setIsTop(entity.getIsTop());
        detail.setLikeTimes(entity.getLikeTimes());
        detail.setCreatedAt(toIsoUtc(entity.getCreatedAt()));
        detail.setUpdatedAt(toIsoUtc(entity.getUpdatedAt()));
        detail.setTalkImgList(photoUrls(id));
        return detail;
    }

    // ==================== 图片 ====================

    private void savePhotos(Integer talkId, List<TalkForm.TalkImgForm> images, LocalDateTime now) {
        if (images == null || images.isEmpty()) {
            return;
        }
        for (TalkForm.TalkImgForm image : images) {
            if (image == null || image.getUrl() == null) {
                continue;
            }
            TalkPhotoEntity photo = new TalkPhotoEntity();
            photo.setTalkId(talkId);
            photo.setUrl(image.getUrl());
            photo.setCreatedAt(now);
            photo.setUpdatedAt(now);
            talkPhotoMapper.insert(photo);
        }
    }

    private void deletePhotos(Integer talkId) {
        if (talkId == null) {
            return;
        }
        talkPhotoMapper.delete(new LambdaQueryWrapper<TalkPhotoEntity>()
                .eq(TalkPhotoEntity::getTalkId, talkId));
    }

    /**
     * 旧图不再被任何说说引用时才删 OSS 对象；外部/本地 URL 会自动跳过。
     */
    private void deleteUnusedPhotoUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String url : urls) {
            if (url != null && !url.isBlank()) {
                unique.add(url);
            }
        }
        if (unique.isEmpty()) {
            return;
        }
        List<TalkPhotoEntity> remaining = talkPhotoMapper.selectList(
                new LambdaQueryWrapper<TalkPhotoEntity>()
                        .select(TalkPhotoEntity::getUrl)
                        .in(TalkPhotoEntity::getUrl, unique));
        Set<String> stillUsed = new HashSet<>();
        for (TalkPhotoEntity photo : remaining) {
            if (photo.getUrl() != null) {
                stillUsed.add(photo.getUrl());
            }
        }
        for (String url : unique) {
            if (!stillUsed.contains(url)) {
                ossService.deleteByUrl(url);
            }
        }
    }

    private List<String> photoUrls(Integer talkId) {
        List<String> urls = new ArrayList<>();
        for (TalkPhotoEntity photo : talkPhotoMapper.selectList(
                new LambdaQueryWrapper<TalkPhotoEntity>()
                        .eq(TalkPhotoEntity::getTalkId, talkId)
                        .orderByAsc(TalkPhotoEntity::getId))) {
            urls.add(photo.getUrl());
        }
        return urls;
    }

    private Map<Integer, List<String>> photoMap(List<Integer> talkIds) {
        Map<Integer, List<String>> map = new HashMap<>();
        if (talkIds.isEmpty()) {
            return map;
        }
        for (TalkPhotoEntity photo : talkPhotoMapper.selectList(
                new LambdaQueryWrapper<TalkPhotoEntity>()
                        .in(TalkPhotoEntity::getTalkId, talkIds)
                        .orderByAsc(TalkPhotoEntity::getId))) {
            map.computeIfAbsent(photo.getTalkId(), k -> new ArrayList<>()).add(photo.getUrl());
        }
        return map;
    }

    // ==================== 组装 ====================

    private List<TalkView> toViews(List<TalkEntity> rows, boolean withLike, Integer userId, String ip) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Integer> talkIds = rows.stream().map(TalkEntity::getId).toList();
        Map<Integer, List<String>> photos = photoMap(talkIds);

        Set<Integer> userIds = new LinkedHashSet<>();
        for (TalkEntity row : rows) {
            if (row.getUserId() != null) {
                userIds.add(row.getUserId());
            }
        }
        Map<Integer, UserEntity> users = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (UserEntity user : userMapper.selectBatchIds(userIds)) {
                users.put(user.getId(), user);
            }
        }

        boolean byUser = userId != null && userId != 0;
        List<TalkView> list = new ArrayList<>(rows.size());
        for (TalkEntity row : rows) {
            TalkView view = new TalkView();
            BeanUtils.copyProperties(row, view);
            List<String> urls = photos.get(row.getId());
            if (urls != null && !urls.isEmpty()) {
                view.setTalkImgList(urls);
            }
            UserEntity user = users.get(row.getUserId());
            if (user != null) {
                view.setNickName(user.getNickName());
                view.setAvatar(user.getAvatar());
            }
            if (withLike) {
                view.setIsLike(byUser
                        ? likeService.isLikedByUser(row.getId(), 2, userId)
                        : likeService.isLikedByIp(row.getId(), 2, ip));
            }
            list.add(view);
        }
        return list;
    }

    /** 本地时间是 Asia/Shanghai，转成 UTC 后按 ISO 毫秒格式输出（对齐 JSON.stringify(Date)） */
    private String toIsoUtc(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.minusHours(8).format(ISO_UTC);
    }
}
