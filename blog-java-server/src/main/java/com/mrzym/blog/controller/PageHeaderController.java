package com.mrzym.blog.controller;

import com.mrzym.blog.auth.RequireRole;
import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.common.Result;
import com.mrzym.blog.dto.PageHeaderDeleteForm;
import com.mrzym.blog.dto.PageHeaderForm;
import com.mrzym.blog.dto.PageHeaderItem;
import com.mrzym.blog.entity.PageHeaderEntity;
import com.mrzym.blog.service.PageHeaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/pageHeader")
@RequiredArgsConstructor
public class PageHeaderController {

    private final PageHeaderService pageHeaderService;

    /** 全站背景图，前台各页面按 route_name 取用 */
    @GetMapping("/getAll")
    public Result<List<PageHeaderItem>> getAll() {
        try {
            return Result.ok("获取所有背景成功", pageHeaderService.getAll());
        } catch (Exception e) {
            log.error("获取所有背景失败", e);
            throw new BizException(ErrorCode.HEADER, "获取所有背景失败");
        }
    }

    /**
     * 新增/修改背景（登录 + 管理员）。route_name 必须唯一。
     *
     * 与 Node 实现的差异：原实现在“改名到一个未被占用的路径”时，
     * getOneByPath 返回 null 后直接读 flag.id，会抛 TypeError（500），
     * 这里修正为允许改名，见 README「已知差异」。
     */
    @PostMapping("/addOrUpdate")
    @RequireRole(RequireRole.Level.ADMIN)
    public Result<Boolean> addOrUpdate(@RequestBody PageHeaderForm form) {
        Integer id = Num.toInteger(form.getId());
        boolean update = id != null && id != 0;
        String action = update ? "修改" : "新增";

        PageHeaderEntity same = pageHeaderService.getOneByPath(form.getRouteName());
        if (!update) {
            if (same != null) {
                throw new BizException(ErrorCode.HEADER, "已经存在相同的背景路径");
            }
        } else if (same != null && !Objects.equals(same.getId(), id)) {
            throw new BizException(ErrorCode.HEADER, "已经存在相同的背景路径");
        }

        try {
            boolean res = pageHeaderService.addOrUpdate(update ? id : null,
                    form.getRouteName(), form.getBgUrl());
            return Result.ok(action + "背景成功", res);
        } catch (Exception e) {
            log.error("{}背景失败", action, e);
            throw new BizException(ErrorCode.HEADER, action + "背景失败");
        }
    }

    /** 删除背景（登录 + 管理员）。返回删除条数，0 条时 result 为 null。 */
    @PostMapping("/delete")
    @RequireRole(RequireRole.Level.ADMIN)
    public Result<Integer> delete(@RequestBody PageHeaderDeleteForm form) {
        try {
            Integer affected = pageHeaderService.delete(Num.toInteger(form.getId()));
            if (form.getUrl() != null && !form.getUrl().isBlank()) {
                // TODO 上传模块（qiniu/minio）：原实现在这两种模式下会远程删图，
                //  依赖 utils/qiniuUpload、utils/minioUpload，等上传模块一起补。
                log.debug("跳过远程删图: {}", form.getUrl());
            }
            return Result.ok("删除背景成功", affected);
        } catch (Exception e) {
            log.error("删除背景失败", e);
            throw new BizException(ErrorCode.HEADER, "删除背景失败");
        }
    }
}
