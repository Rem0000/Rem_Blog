package com.mrzym.blog.controller;

import com.mrzym.blog.auth.RequireRole;
import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.CurrentPageResult;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.common.IpUtil;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.common.Result;
import com.mrzym.blog.dto.TalkDetail;
import com.mrzym.blog.dto.TalkForm;
import com.mrzym.blog.dto.TalkQuery;
import com.mrzym.blog.dto.TalkView;
import com.mrzym.blog.limit.RateLimit;
import com.mrzym.blog.service.TalkService;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 说说，对齐原实现 router/talk.js。
 */
@Slf4j
@RestController
@RequestMapping("/talk")
@RequiredArgsConstructor
public class TalkController {

    private final TalkService talkService;

    /** 发布说说 */
    @PostMapping("/publishTalk")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<Map<String, Object>> publishTalk(@RequestBody TalkForm form) {
        try {
            Integer id = talkService.publish(form);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", id);
            return Result.ok("发布说说成功", data);
        } catch (Exception e) {
            log.error("发布说说失败", e);
            throw new BizException(ErrorCode.TALK, "发布说说失败");
        }
    }

    /** 修改说说（图片关联整体重建） */
    @PutMapping("/updateTalk")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<Boolean> updateTalk(@RequestBody TalkForm form) {
        try {
            return Result.ok("修改说说成功", talkService.update(form));
        } catch (Exception e) {
            log.error("修改说说失败", e);
            throw new BizException(ErrorCode.TALK, "修改说说失败");
        }
    }

    /**
     * 删除说说：status 1/2 进回收站（原实现返回 [count]），3 永久删除（返回 count）。
     */
    @DeleteMapping("/deleteTalkById/{id}/{status}")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<Object> deleteTalkById(@PathVariable Integer id, @PathVariable String status) {
        int statusValue = Num.toInt(status, 0);
        String message = statusValue == 3 ? "删除" : "回收";
        try {
            if (statusValue == 1 || statusValue == 2) {
                return Result.ok(message + "说说成功", List.of(talkService.recycle(id)));
            }
            return Result.ok(message + "说说成功", talkService.destroy(id));
        } catch (Exception e) {
            log.error("{}说说失败", message, e);
            throw new BizException(ErrorCode.TALK, message + "说说失败");
        }
    }

    /** 公开/私密（status 就是目标状态） */
    @PutMapping("/togglePublic/{id}/{status}")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<Boolean> togglePublic(@PathVariable Integer id, @PathVariable String status) {
        int value = Num.toInt(status, 0);
        String message = value == 1 ? "公开" : "私密";
        try {
            return Result.ok(message + "说说成功", talkService.togglePublic(id, value));
        } catch (Exception e) {
            log.error("{}说说失败", message, e);
            throw new BizException(ErrorCode.TALK, message + "说说失败");
        }
    }

    /** 置顶/取消置顶 */
    @PutMapping("/toggleTop/{id}/{is_top}")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<Boolean> toggleTop(@PathVariable Integer id,
                                     @PathVariable("is_top") String isTop) {
        int value = Num.toInt(isTop, 0);
        String message = value == 1 ? "置顶" : "取消置顶";
        try {
            return Result.ok(message + "说说成功", talkService.toggleTop(id, value));
        } catch (Exception e) {
            log.error("{}说说失败", message, e);
            throw new BizException(ErrorCode.TALK, message + "说说失败");
        }
    }

    /** 从回收站恢复 */
    @PutMapping("/revertTalk/{id}")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<Boolean> revertTalk(@PathVariable Integer id) {
        try {
            return Result.ok("恢复说说成功", talkService.revert(id));
        } catch (Exception e) {
            log.error("恢复说说失败", e);
            throw new BizException(ErrorCode.TALK, "恢复说说失败");
        }
    }

    /** 后台分页（公开） */
    @PostMapping("/getTalkList")
    public Result<CurrentPageResult<TalkView>> getTalkList(@RequestBody TalkQuery query) {
        try {
            return Result.ok("获取说说列表成功", talkService.getTalkList(query));
        } catch (Exception e) {
            log.error("获取说说列表失败", e);
            throw new BizException(ErrorCode.TALK, "获取说说列表失败");
        }
    }

    /** 详情（公开） */
    @GetMapping("/getTalkById/{id}")
    public Result<TalkDetail> getTalkById(@PathVariable Integer id) {
        try {
            TalkDetail detail = talkService.getTalkById(id);
            if (detail == null) {
                throw new BizException(ErrorCode.TALK, "获取说说详情失败");
            }
            return Result.ok("获取说说详情成功", detail);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取说说详情失败", e);
            throw new BizException(ErrorCode.TALK, "获取说说详情失败");
        }
    }

    /** 点赞（1 分钟 5 次） */
    @PutMapping("/like/{id}")
    @RateLimit(key = "talk:like", max = 5, windowSeconds = 60, message = "说说点赞过于频繁 请稍后再试")
    public Result<Boolean> like(@PathVariable Integer id) {
        try {
            return Result.ok("点赞成功", talkService.like(id));
        } catch (Exception e) {
            log.error("点赞失败", e);
            throw new BizException(ErrorCode.TALK, "点赞失败");
        }
    }

    /** 取消点赞（1 分钟 5 次） */
    @PutMapping("/cancelLike/{id}")
    @RateLimit(key = "talk:cancelLike", max = 5, windowSeconds = 60, message = "说说取消点赞过于频繁 请稍后再试")
    public Result<Boolean> cancelLike(@PathVariable Integer id) {
        try {
            return Result.ok("取消点赞成功", talkService.cancelLike(id));
        } catch (Exception e) {
            log.error("取消点赞失败", e);
            throw new BizException(ErrorCode.TALK, "取消点赞失败");
        }
    }

    /** 前台分页（只出公开 + is_like） */
    @PostMapping("/blogGetTalkList")
    public Result<CurrentPageResult<TalkView>> blogGetTalkList(@RequestBody TalkQuery query,
                                                               HttpServletRequest request) {
        try {
            String ip = IpUtil.stripPort(IpUtil.clientIp(request));
            return Result.ok("获取说说列表成功", talkService.blogGetTalkList(query, ip));
        } catch (Exception e) {
            log.error("获取说说列表失败", e);
            throw new BizException(ErrorCode.TALK, "获取说说列表失败");
        }
    }
}
