package com.mrzym.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mrzym.blog.common.CurrentPageResult;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.dto.PhotoAddRequest;
import com.mrzym.blog.dto.PhotoQuery;
import com.mrzym.blog.entity.PhotoEntity;
import com.mrzym.blog.mapper.PhotoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 相册图片，对齐原实现 service/photo。
 */
@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoMapper photoMapper;
    private final OssService ossService;

    /** 批量新增，返回创建出来的行（对齐 Sequelize bulkCreate） */
    public List<PhotoEntity> add(List<PhotoAddRequest.PhotoItem> items) {
        List<PhotoEntity> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        LocalDateTime now = LocalDateTime.now().withNano(0);
        for (PhotoAddRequest.PhotoItem item : items) {
            if (item == null) {
                continue;
            }
            PhotoEntity entity = new PhotoEntity();
            entity.setAlbumId(Num.toInteger(item.getAlbumId()));
            entity.setUrl(item.getUrl());
            Integer status = Num.toInteger(item.getStatus());
            entity.setStatus(status == null ? 1 : status);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            photoMapper.insert(entity);
            result.add(entity);
        }
        return result;
    }

    /** type 1 进回收站（返回 [count]），其它永久删除（返回 count） */
    public Object delete(List<Integer> idList, int type) {
        if (idList == null || idList.isEmpty()) {
            return type == 1 ? List.of(0) : 0;
        }
        if (type == 1) {
            int affected = photoMapper.update(null, new LambdaUpdateWrapper<PhotoEntity>()
                    .set(PhotoEntity::getStatus, 2)
                    .set(PhotoEntity::getUpdatedAt, LocalDateTime.now().withNano(0))
                    .in(PhotoEntity::getId, idList));
            return List.of(affected);
        }
        List<PhotoEntity> photos = photoMapper.selectBatchIds(idList);
        int affected = photoMapper.deleteBatchIds(idList);
        if (affected > 0) {
            deleteUnusedPhotoUrls(photos);
        }
        return affected;
    }

    /** 批量恢复（status -> 1），返回 [count] */
    public List<Integer> revert(List<Integer> idList) {
        int affected = photoMapper.update(null, new LambdaUpdateWrapper<PhotoEntity>()
                .set(PhotoEntity::getStatus, 1)
                .set(PhotoEntity::getUpdatedAt, LocalDateTime.now().withNano(0))
                .in(PhotoEntity::getId, idList));
        return List.of(affected);
    }

    /** 分页：album_id / status 传了才过滤（Sequelize 会忽略 undefined） */
    public CurrentPageResult<PhotoEntity> list(PhotoQuery query) {
        Object rawCurrent = query.getCurrent();
        Object rawSize = query.getSize();
        int pageNo = Math.max(Num.toInt(rawCurrent, 1), 1);
        int pageSize = Num.toInt(rawSize, 10);
        if (pageSize <= 0) {
            pageSize = 10;
        }

        LambdaQueryWrapper<PhotoEntity> wrapper = new LambdaQueryWrapper<>();
        Integer albumId = Num.toInteger(query.getId());
        if (albumId != null) {
            wrapper.eq(PhotoEntity::getAlbumId, albumId);
        }
        Integer status = Num.toInteger(query.getStatus());
        if (status != null) {
            wrapper.eq(PhotoEntity::getStatus, status);
        }

        Page<PhotoEntity> page = photoMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return CurrentPageResult.of(
                rawCurrent != null ? rawCurrent : pageNo,
                rawSize != null ? rawSize : pageSize,
                page.getTotal(),
                page.getRecords());
    }

    /** 相册下所有正常图片，按创建时间倒序 */
    public List<PhotoEntity> getAllByAlbumId(Integer albumId) {
        return photoMapper.selectList(new LambdaQueryWrapper<PhotoEntity>()
                .eq(PhotoEntity::getAlbumId, albumId)
                .eq(PhotoEntity::getStatus, 1)
                .orderByDesc(PhotoEntity::getCreatedAt));
    }

    public void deleteByAlbumId(Integer albumId) {
        List<PhotoEntity> photos = photoMapper.selectList(new LambdaQueryWrapper<PhotoEntity>()
                .eq(PhotoEntity::getAlbumId, albumId));
        photoMapper.delete(new LambdaQueryWrapper<PhotoEntity>()
                .eq(PhotoEntity::getAlbumId, albumId));
        deleteUnusedPhotoUrls(photos);
    }

    private void deleteUnusedPhotoUrls(List<PhotoEntity> photos) {
        if (photos == null || photos.isEmpty()) {
            return;
        }
        Set<String> urls = new HashSet<>();
        for (PhotoEntity photo : photos) {
            if (photo != null && photo.getUrl() != null && !photo.getUrl().isBlank()) {
                urls.add(photo.getUrl());
            }
        }
        if (urls.isEmpty()) {
            return;
        }
        List<PhotoEntity> remaining = photoMapper.selectList(new LambdaQueryWrapper<PhotoEntity>()
                .select(PhotoEntity::getUrl)
                .in(PhotoEntity::getUrl, urls));
        Set<String> stillUsed = new HashSet<>();
        for (PhotoEntity photo : remaining) {
            if (photo.getUrl() != null) {
                stillUsed.add(photo.getUrl());
            }
        }
        for (String url : urls) {
            if (!stillUsed.contains(url)) {
                ossService.deleteByUrl(url);
            }
        }
    }
}
