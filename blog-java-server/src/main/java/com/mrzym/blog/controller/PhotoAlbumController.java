package com.mrzym.blog.controller;

import com.mrzym.blog.auth.RequireRole;
import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.CurrentPageResult;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.common.Result;
import com.mrzym.blog.dto.PhotoAlbumForm;
import com.mrzym.blog.dto.PhotoAlbumQuery;
import com.mrzym.blog.entity.PhotoAlbumEntity;
import com.mrzym.blog.service.PhotoAlbumService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * 相册，对齐原实现 router/photoAlbum.js。
 */
@Slf4j
@RestController
@RequestMapping("/photoAlbum")
@RequiredArgsConstructor
public class PhotoAlbumController {

    private final PhotoAlbumService photoAlbumService;

    /** 新增相册（名称唯一） */
    @PostMapping("/add")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<PhotoAlbumEntity> add(@RequestBody PhotoAlbumForm form) {
        PhotoAlbumEntity exist = photoAlbumService.getOne(null, form.getAlbumName());
        if (exist != null) {
            throw new BizException(ErrorCode.PHOTO_ALBUM, "已经存在相同的相册名称，换一个试试");
        }
        try {
            return Result.ok("创建相册成功", photoAlbumService.add(form));
        } catch (Exception e) {
            log.error("创建相册失败", e);
            throw new BizException(ErrorCode.PHOTO_ALBUM, "创建相册失败");
        }
    }

    /** 删除相册（同时删掉相册下的图片和 OSS 旧图） */
    @DeleteMapping("/delete/{id}")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<Boolean> delete(@PathVariable Integer id) {
        try {
            return Result.ok("删除相册成功", photoAlbumService.delete(id));
        } catch (Exception e) {
            log.error("删除相册失败", e);
            throw new BizException(ErrorCode.PHOTO_ALBUM, "删除相册失败");
        }
    }

    /** 修改相册（名称唯一，排除自己） */
    @PutMapping("/update")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<Boolean> update(@RequestBody PhotoAlbumForm form) {
        Integer id = Num.toInteger(form.getId());
        PhotoAlbumEntity exist = photoAlbumService.getOne(null, form.getAlbumName());
        if (exist != null && !Objects.equals(exist.getId(), id)) {
            throw new BizException(ErrorCode.PHOTO_ALBUM, "已经存在相同的相册名称，换一个试试");
        }
        try {
            return Result.ok("修改相册成功", photoAlbumService.update(form));
        } catch (Exception e) {
            log.error("修改相册失败", e);
            throw new BizException(ErrorCode.PHOTO_ALBUM, "修改相册失败");
        }
    }

    /** 分页获取相册（注意原实现的路径是 /photoAlbum/） */
    @PostMapping({"", "/"})
    public Result<CurrentPageResult<PhotoAlbumEntity>> list(@RequestBody PhotoAlbumQuery query) {
        try {
            return Result.ok("获取相册列表成功", photoAlbumService.list(query));
        } catch (Exception e) {
            log.error("获取相册列表失败", e);
            throw new BizException(ErrorCode.PHOTO_ALBUM, "获取相册列表失败");
        }
    }

    /** 所有相册（创建时间倒序） */
    @GetMapping("/getAllAlbumList")
    public Result<List<PhotoAlbumEntity>> getAllAlbumList() {
        try {
            return Result.ok("获取所有相册列表成功", photoAlbumService.getAll());
        } catch (Exception e) {
            log.error("获取所有相册列表失败", e);
            throw new BizException(ErrorCode.PHOTO_ALBUM, "获取所有相册列表失败");
        }
    }
}
