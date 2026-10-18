package com.mrzym.blog.controller;

import com.mrzym.blog.auth.RequireRole;
import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.CurrentPageResult;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.common.Result;
import com.mrzym.blog.dto.IdListRequest;
import com.mrzym.blog.dto.LinkForm;
import com.mrzym.blog.dto.LinkQuery;
import com.mrzym.blog.entity.LinkEntity;
import com.mrzym.blog.limit.RateLimit;
import com.mrzym.blog.service.LinkService;
import com.mrzym.blog.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 友链，对齐原实现 router/links.js + controller/links/index.js。
 */
@Slf4j
@RestController
@RequestMapping("/links")
@RequiredArgsConstructor
public class LinksController {

    private final LinkService linkService;
    private final NotifyService notifyService;

    /**
     * 新增友链（公开）。原实现挂了 createTimesLimiter：1 分钟 5 次，
     * 提示语「新增过于频繁 请稍后再试」。
     */
    @PostMapping("/add")
    @RateLimit(key = "links:add", max = 5, windowSeconds = 60, message = "新增过于频繁 请稍后再试")
    public Result<Boolean> add(@RequestBody LinkForm form) {
        String action = linkService.isUpdate(form) ? "修改" : "发布";
        try {
            boolean res = linkService.addOrUpdate(form);
            if (!linkService.isUpdate(form)) {
                notifyService.addNotifyQuietly(1, 4, null,
                        "您的收到了来自于：" + form.getSiteName() + " 的友链申请，点我去后台审核！");
            }
            return Result.ok(action + "友链成功", res);
        } catch (Exception e) {
            log.error("{}友链失败", action, e);
            throw new BizException(ErrorCode.LINKS, action + "友链失败");
        }
    }

    /** 博客前台修改友链（公开）。无论带不带 id 都会发一条“修改申请”通知。 */
    @PostMapping("/frontUpdate")
    public Result<Boolean> frontUpdate(@RequestBody LinkForm form) {
        try {
            boolean res = linkService.addOrUpdate(form);
            notifyService.addNotifyQuietly(1, 4, null,
                    "您的收到了来自于：" + form.getSiteName() + " 的友链修改申请，点我去后台审核！");
            return Result.ok("修改友链成功", res);
        } catch (Exception e) {
            log.error("修改友链失败", e);
            throw new BizException(ErrorCode.LINKS, "修改友链失败");
        }
    }

    /** 博客后台修改友链（登录 + 管理员，非超管） */
    @PostMapping("/backUpdate")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<Boolean> backUpdate(@RequestBody LinkForm form) {
        String action = linkService.isUpdate(form) ? "修改" : "发布";
        try {
            boolean res = linkService.addOrUpdate(form);
            if (!linkService.isUpdate(form)) {
                notifyService.addNotifyQuietly(1, 4, null,
                        "您的收到了来自于：" + form.getSiteName() + " 的友链申请，点我去后台审核！");
            }
            return Result.ok(action + "友链成功", res);
        } catch (Exception e) {
            log.error("{}友链失败", action, e);
            throw new BizException(ErrorCode.LINKS, action + "友链失败");
        }
    }

    /** 批量删除（登录 + 管理员，非超管）。注意原实现的提示语前面有一个空格。 */
    @PutMapping("/delete")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<Integer> delete(@RequestBody IdListRequest request) {
        try {
            Integer affected = linkService.deleteLinks(request.getIdList());
            return Result.ok(" 删除友链成功", affected);
        } catch (Exception e) {
            log.error("删除友链失败", e);
            throw new BizException(ErrorCode.LINKS, "删除友链失败");
        }
    }

    /** 批量审核（登录 + 管理员，非超管）。原实现把 Sequelize 的 [count] 直接返回。 */
    @PutMapping("/approve")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<List<Integer>> approve(@RequestBody IdListRequest request) {
        try {
            int affected = linkService.approveLinks(request.getIdList());
            return Result.ok("审核友链成功", List.of(affected));
        } catch (Exception e) {
            log.error("审核友链失败", e);
            throw new BizException(ErrorCode.LINKS, "审核友链失败");
        }
    }

    /** 分页获取友链（公开），入参 current / size，出参 {current,size,list,total} */
    @PostMapping("/getLinksList")
    public Result<CurrentPageResult<LinkEntity>> getLinksList(@RequestBody LinkQuery query) {
        try {
            return Result.ok("查询友链成功", linkService.getLinksList(query));
        } catch (Exception e) {
            log.error("查询友链失败", e);
            throw new BizException(ErrorCode.LINKS, "查询友链失败");
        }
    }
}
