package com.mrzym.blog.controller;

import com.mrzym.blog.auth.RequireRole;
import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.common.IpUtil;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.common.Result;
import com.mrzym.blog.common.SensitiveWordFilter;
import com.mrzym.blog.dto.CommentAddForm;
import com.mrzym.blog.dto.CommentBackQuery;
import com.mrzym.blog.dto.CommentFrontQuery;
import com.mrzym.blog.dto.CommentPageResult;
import com.mrzym.blog.dto.CommentTotalQuery;
import com.mrzym.blog.dto.CommentView;
import com.mrzym.blog.entity.CommentEntity;
import com.mrzym.blog.limit.RateLimit;
import com.mrzym.blog.service.CommentService;
import com.mrzym.blog.service.NotifyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 评论，对齐原实现 router/comment.js。
 *
 * 错误码照抄原实现的 bug：controller 顶部用的是 ERRORCODE.CATEGORY（100004）。
 */
@Slf4j
@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
public class CommentController {

    private static final String CODE = ErrorCode.CATEGORY;

    private final CommentService commentService;
    private final NotifyService notifyService;

    /** 新增评论（1 分钟 20 次） */
    @PostMapping("/add")
    @RateLimit(key = "comment:add", max = 20, windowSeconds = 60, message = "评论过于频繁 请稍后再试")
    public Result<Map<String, Object>> add(@RequestBody CommentAddForm form, HttpServletRequest request) {
        try {
            String content = SensitiveWordFilter.filter(form.getContent());
            String ip = IpUtil.stripPort(IpUtil.clientIp(request));
            CommentEntity created = commentService.add(form, ip, content);

            Integer fromId = Num.toInteger(form.getFromId());
            Integer authorId = Num.toInteger(form.getAuthorId());
            if (!Objects.equals(fromId, authorId)) {
                notifyService.addNotifyQuietly(authorId, Num.toInteger(form.getType()),
                        Num.toInteger(form.getForId()),
                        "您的" + commentService.typeName(Num.toInteger(form.getType()))
                                + "收到了来自于：" + form.getFromName() + " 的评论: " + content + "！");
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("res", created);
            return Result.ok("新增评论成功", data);
        } catch (Exception e) {
            log.error("新增评论失败", e);
            throw new BizException(CODE, "新增评论失败");
        }
    }

    /** 回复评论（1 分钟 20 次） */
    @PostMapping("/apply")
    @RateLimit(key = "comment:apply", max = 20, windowSeconds = 60, message = "回复评论过于频繁 请稍后再试")
    public Result<Map<String, Object>> apply(@RequestBody CommentAddForm form, HttpServletRequest request) {
        try {
            String content = SensitiveWordFilter.filter(form.getContent());
            String ip = IpUtil.stripPort(IpUtil.clientIp(request));
            CommentEntity created = commentService.apply(form, ip, content);

            Integer fromId = Num.toInteger(form.getFromId());
            Integer toId = Num.toInteger(form.getToId());
            if (!Objects.equals(fromId, toId)) {
                notifyService.addNotifyQuietly(toId, Num.toInteger(form.getType()),
                        Num.toInteger(form.getForId()),
                        "您的收到了来自于：" + form.getFromName() + " 的评论回复: " + content + "！");
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("res", created);
            return Result.ok("回复评论成功", data);
        } catch (Exception e) {
            log.error("回复评论失败", e);
            throw new BizException(CODE, "回复评论失败");
        }
    }

    /** 点赞评论（1 分钟 10 次） */
    @PutMapping("/thumbUp/{id}")
    @RateLimit(key = "comment:thumbUp", max = 10, windowSeconds = 60, message = "点赞过于频繁 请稍后再试")
    public Result<Map<String, Object>> thumbUp(@PathVariable Integer id) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("res", commentService.like(id));
            return Result.ok("点赞成功", data);
        } catch (Exception e) {
            log.error("点赞失败", e);
            throw new BizException(CODE, "点赞失败");
        }
    }

    /** 取消点赞评论（1 分钟 10 次） */
    @PutMapping("/cancelCommentLike/{id}")
    @RateLimit(key = "comment:cancel", max = 10, windowSeconds = 60, message = "取消点赞过于频繁 请稍后再试")
    public Result<Map<String, Object>> cancelLike(@PathVariable Integer id) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("res", commentService.cancelLike(id));
            return Result.ok("取消点赞成功", data);
        } catch (Exception e) {
            log.error("取消点赞失败", e);
            throw new BizException(CODE, "取消点赞失败");
        }
    }

    /** 前台删除评论（登录） */
    @DeleteMapping("/delete/{id}/{parent_id}")
    @RequireRole(RequireRole.Level.LOGIN)
    public Result<Map<String, Object>> delete(@PathVariable Integer id,
                                              @PathVariable("parent_id") String parentId) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("res", commentService.delete(id, Num.toInt(parentId, 0)));
            return Result.ok("删除评论成功", data);
        } catch (Exception e) {
            log.error("删除评论失败", e);
            throw new BizException(CODE, "删除评论失败");
        }
    }

    /** 后台删除评论（管理员，非超管） */
    @DeleteMapping("/backDelete/{id}/{parent_id}")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<Map<String, Object>> backDelete(@PathVariable Integer id,
                                                  @PathVariable("parent_id") String parentId) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("res", commentService.delete(id, Num.toInt(parentId, 0)));
            return Result.ok("删除评论成功", data);
        } catch (Exception e) {
            log.error("删除评论失败", e);
            throw new BizException(CODE, "删除评论失败");
        }
    }

    /** 后台分页（公开） */
    @PostMapping("/backGetCommentList")
    public Result<CommentPageResult<CommentView>> backGetCommentList(@RequestBody CommentBackQuery query) {
        try {
            return Result.ok("分页查找评论成功", commentService.backList(query));
        } catch (Exception e) {
            log.error("分页查找评论失败", e);
            throw new BizException(CODE, "分页查找评论失败");
        }
    }

    /** 前台父级评论分页 */
    @PostMapping("/frontGetParentComment")
    public Result<CommentPageResult<CommentView>> frontGetParentComment(
            @RequestBody CommentFrontQuery query, HttpServletRequest request) {
        try {
            String ip = IpUtil.stripPort(IpUtil.clientIp(request));
            return Result.ok("分页查找评论成功", commentService.frontParents(query, ip));
        } catch (Exception e) {
            log.error("分页查找评论失败", e);
            throw new BizException(CODE, "分页查找评论失败");
        }
    }

    /** 前台子评论分页 */
    @PostMapping("/frontGetChildrenComment")
    public Result<CommentPageResult<CommentView>> frontGetChildrenComment(
            @RequestBody CommentFrontQuery query, HttpServletRequest request) {
        try {
            String ip = IpUtil.stripPort(IpUtil.clientIp(request));
            return Result.ok("分页查找子评论成功", commentService.frontChildren(query, ip));
        } catch (Exception e) {
            log.error("分页查找子评论失败", e);
            throw new BizException(CODE, "分页查找子评论失败");
        }
    }

    /** 评论总数（父 + 子） */
    @PostMapping("/getCommentTotal")
    public Result<Long> getCommentTotal(@RequestBody CommentTotalQuery query) {
        try {
            return Result.ok("获取评论总条数成功", commentService.total(query.getForId(), query.getType()));
        } catch (Exception e) {
            log.error("获取评论总条数失败", e);
            throw new BizException(CODE, "获取评论总条数失败");
        }
    }
}
