package com.mrzym.blog.controller;

import com.mrzym.blog.auth.RequireRole;
import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.CurrentPageResult;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.common.Result;
import com.mrzym.blog.dto.IdListRequest;
import com.mrzym.blog.dto.PhotoAddRequest;
import com.mrzym.blog.dto.PhotoDeleteRequest;
import com.mrzym.blog.dto.PhotoQuery;
import com.mrzym.blog.entity.PhotoEntity;
import com.mrzym.blog.service.PhotoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 相册图片，对齐原实现 router/photo.js。
 */
@Slf4j
@RestController
@RequestMapping("/photo")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;

    /** 批量新增图片 */
    @PostMapping("/add")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<List<PhotoEntity>> add(@RequestBody PhotoAddRequest request) {
        try {
            return Result.ok("新增图片成功", photoService.add(request.getPhotoList()));
        } catch (Exception e) {
            log.error("新增图片失败", e);
            throw new BizException(ErrorCode.PHOTO, "新增图片失败");
        }
    }

    /** 批量删除：type 1 进回收站，其它永久删除 */
    @PutMapping("/delete")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<Object> delete(@RequestBody PhotoDeleteRequest request) {
        try {
            List<Integer> idList = new ArrayList<>();
            if (request.getImgList() != null) {
                for (PhotoDeleteRequest.ImgItem item : request.getImgList()) {
                    Integer id = Num.toInteger(item.getId());
                    if (id != null) {
                        idList.add(id);
                    }
                }
            }
            int type = Num.toInt(request.getType(), 0);
            if (type == 2 && request.getImgList() != null) {
                // TODO 上传模块：qiniu/minio 模式下按 url 删除远端图片
                log.debug("跳过远程删图，共 {} 张", request.getImgList().size());
            }
            return Result.ok("删除图片成功", photoService.delete(idList, type));
        } catch (Exception e) {
            log.error("删除图片失败", e);
            throw new BizException(ErrorCode.PHOTO, "删除图片失败");
        }
    }

    /** 批量恢复（回收站 -> 正常） */
    @PutMapping("/revert")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<List<Integer>> revert(@RequestBody IdListRequest request) {
        try {
            return Result.ok("恢复图片成功", photoService.revert(request.getIdList()));
        } catch (Exception e) {
            log.error("恢复图片失败", e);
            throw new BizException(ErrorCode.PHOTO, "恢复图片失败");
        }
    }

    /** 按相册分页 */
    @PostMapping("/getPhotoListByAlbumId")
    public Result<CurrentPageResult<PhotoEntity>> getPhotoListByAlbumId(@RequestBody PhotoQuery query) {
        try {
            return Result.ok("获取相册图片成功", photoService.list(query));
        } catch (Exception e) {
            log.error("获取相册图片失败", e);
            throw new BizException(ErrorCode.PHOTO, "获取相册图片失败");
        }
    }

    /** 相册下所有正常图片（创建时间倒序） */
    @GetMapping("/getAllPhotosByAlbumId/{id}")
    public Result<List<PhotoEntity>> getAllPhotosByAlbumId(@PathVariable Integer id) {
        try {
            return Result.ok("获取相册所有照片成功", photoService.getAllByAlbumId(id));
        } catch (Exception e) {
            log.error("获取相册所有照片失败", e);
            throw new BizException(ErrorCode.PHOTO, "获取相册所有照片失败");
        }
    }
}
