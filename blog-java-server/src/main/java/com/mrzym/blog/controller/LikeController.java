package com.mrzym.blog.controller;

import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.common.IpUtil;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.common.Result;
import com.mrzym.blog.dto.LikeForm;
import com.mrzym.blog.limit.RateLimit;
import com.mrzym.blog.service.LikeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点赞记录，对齐原实现 router/like.js。
 * 点赞数同步：type 1 文章 / 2 说说 / 3 留言 / 4 评论。
 */
@Slf4j
@RestController
@RequestMapping("/like")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @PostMapping("/addLike")
    @RateLimit(key = "like:add", max = 10, windowSeconds = 60, message = "点赞过于频繁 请稍后再试")
    public Result<Boolean> addLike(@RequestBody LikeForm form, HttpServletRequest request) {
        Integer forId = Num.toInteger(form.getForId());
        Integer type = Num.toInteger(form.getType());
        if (forId == null || forId == 0) {
            throw new BizException(ErrorCode.LIKE, "点赞对象不能为空");
        }
        if (type == null || type == 0) {
            throw new BizException(ErrorCode.LIKE, "点赞类型不能为空");
        }
        Integer userId = Num.toInteger(form.getUserId());
        boolean byUser = userId != null && userId != 0;
        String ip = IpUtil.stripPort(IpUtil.clientIp(request));

        try {
            boolean liked = byUser
                    ? likeService.isLikedByUser(forId, type, userId)
                    : likeService.isLikedByIp(forId, type, ip);
            if (liked) {
                throw new BizException(ErrorCode.LIKE, "您已经点过赞了");
            }
            boolean res = likeService.addLike(forId, type, byUser ? userId : null, byUser ? null : ip);
            if (!res) {
                throw new BizException(ErrorCode.LIKE, "点赞失败");
            }
            likeService.changeCounter(type, forId, true);
            return Result.ok("点赞成功", true);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("点赞失败", e);
            throw new BizException(ErrorCode.LIKE, "点赞失败");
        }
    }

    @PostMapping("/cancelLike")
    @RateLimit(key = "like:cancel", max = 10, windowSeconds = 60, message = "取消点赞过于频繁 请稍后再试")
    public Result<Integer> cancelLike(@RequestBody LikeForm form, HttpServletRequest request) {
        Integer forId = Num.toInteger(form.getForId());
        Integer type = Num.toInteger(form.getType());
        if (forId == null || forId == 0) {
            throw new BizException(ErrorCode.LIKE, "取消点赞对象不能为空");
        }
        if (type == null || type == 0) {
            throw new BizException(ErrorCode.LIKE, "取消点赞类型不能为空");
        }
        Integer userId = Num.toInteger(form.getUserId());
        boolean byUser = userId != null && userId != 0;
        String ip = IpUtil.stripPort(IpUtil.clientIp(request));

        try {
            boolean liked = byUser
                    ? likeService.isLikedByUser(forId, type, userId)
                    : likeService.isLikedByIp(forId, type, ip);
            if (!liked) {
                throw new BizException(ErrorCode.LIKE, "您没有点过赞");
            }
            Integer res = likeService.cancelLike(forId, type, byUser ? userId : null, ip);
            if (res == null) {
                throw new BizException(ErrorCode.LIKE, "取消点赞失败");
            }
            likeService.changeCounter(type, forId, false);
            return Result.ok("取消点赞成功", res);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("取消点赞失败", e);
            throw new BizException(ErrorCode.LIKE, "取消点赞失败");
        }
    }

    @PostMapping("/getIsLikeByIdOrIpAndType")
    public Result<Boolean> getIsLike(@RequestBody LikeForm form, HttpServletRequest request) {
        Integer forId = Num.toInteger(form.getForId());
        Integer type = Num.toInteger(form.getType());
        if (forId == null || forId == 0) {
            throw new BizException(ErrorCode.LIKE, "取消点赞对象不能为空");
        }
        if (type == null || type == 0) {
            throw new BizException(ErrorCode.LIKE, "取消点赞类型不能为空");
        }
        Integer userId = Num.toInteger(form.getUserId());
        try {
            boolean res = userId != null && userId != 0
                    ? likeService.isLikedByUser(forId, type, userId)
                    : likeService.isLikedByIp(forId, type,
                    IpUtil.stripPort(IpUtil.clientIp(request)));
            return Result.ok("获取用户是否点赞成功", res);
        } catch (Exception e) {
            log.error("获取用户是否点赞失败", e);
            throw new BizException(ErrorCode.LIKE, "获取用户是否点赞失败");
        }
    }
}
