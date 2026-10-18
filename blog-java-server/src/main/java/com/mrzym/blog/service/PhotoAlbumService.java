package com.mrzym.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mrzym.blog.common.CurrentPageResult;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.dto.PhotoAlbumForm;
import com.mrzym.blog.dto.PhotoAlbumQuery;
import com.mrzym.blog.entity.PhotoAlbumEntity;
import com.mrzym.blog.mapper.PhotoAlbumMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 相册，对齐原实现 service/photoAlbum。
 */
@Service
@RequiredArgsConstructor
public class PhotoAlbumService {

    private final PhotoAlbumMapper photoAlbumMapper;
    private final PhotoService photoService;

    public PhotoAlbumEntity add(PhotoAlbumForm form) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        PhotoAlbumEntity entity = new PhotoAlbumEntity();
        entity.setAlbumName(form.getAlbumName());
        entity.setAlbumCover(form.getAlbumCover());
        entity.setDescription(form.getDescription());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        photoAlbumMapper.insert(entity);
        return entity;
    }

    /** 删相册 + 删该相册下所有图片 */
    public boolean delete(Integer id) {
        int affected = photoAlbumMapper.deleteById(id);
        photoService.deleteByAlbumId(id);
        return affected > 0;
    }

    public boolean update(PhotoAlbumForm form) {
        PhotoAlbumEntity patch = new PhotoAlbumEntity();
        patch.setId(Num.toInteger(form.getId()));
        patch.setAlbumName(form.getAlbumName());
        patch.setAlbumCover(form.getAlbumCover());
        patch.setDescription(form.getDescription());
        patch.setUpdatedAt(LocalDateTime.now().withNano(0));
        return photoAlbumMapper.updateById(patch) > 0;
    }

    public CurrentPageResult<PhotoAlbumEntity> list(PhotoAlbumQuery query) {
        Object rawCurrent = query.getCurrent();
        Object rawSize = query.getSize();
        int pageNo = Math.max(Num.toInt(rawCurrent, 1), 1);
        int pageSize = Num.toInt(rawSize, 10);
        if (pageSize <= 0) {
            pageSize = 10;
        }

        LambdaQueryWrapper<PhotoAlbumEntity> wrapper = new LambdaQueryWrapper<>();
        if (query.getAlbumName() != null && !query.getAlbumName().isBlank()) {
            wrapper.like(PhotoAlbumEntity::getAlbumName, query.getAlbumName());
        }
        Page<PhotoAlbumEntity> page = photoAlbumMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return CurrentPageResult.of(
                rawCurrent != null ? rawCurrent : pageNo,
                rawSize != null ? rawSize : pageSize,
                page.getTotal(),
                page.getRecords());
    }

    public List<PhotoAlbumEntity> getAll() {
        return photoAlbumMapper.selectList(new LambdaQueryWrapper<PhotoAlbumEntity>()
                .orderByDesc(PhotoAlbumEntity::getCreatedAt));
    }

    /** 按 id 或名称查一条，对应 getOneAlbum */
    public PhotoAlbumEntity getOne(Integer id, String albumName) {
        LambdaQueryWrapper<PhotoAlbumEntity> wrapper = new LambdaQueryWrapper<>();
        if (id != null) {
            wrapper.eq(PhotoAlbumEntity::getId, id);
        }
        if (albumName != null && !albumName.isEmpty()) {
            wrapper.eq(PhotoAlbumEntity::getAlbumName, albumName);
        }
        List<PhotoAlbumEntity> list = photoAlbumMapper.selectList(wrapper.last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }
}
