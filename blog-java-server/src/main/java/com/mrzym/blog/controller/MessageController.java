package com.mrzym.blog.controller;

import com.mrzym.blog.auth.RequireRole;
import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.CurrentPageResult;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.common.IpUtil;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.common.Result;
import com.mrzym.blog.common.SensitiveWordFilter;
import com.mrzym.blog.dto.IdListRequest;
import com.mrzym.blog.dto.MessageForm;
import com.mrzym.blog.dto.MessageQuery;
import com.mrzym.blog.dto.MessageTagCount;
import com.mrzym.blog.dto.MessageView;
import com.mrzym.blog.limit.RateLimit;
import com.mrzym.blog.service.MessageService;
import com.mrzym.blog.service.NotifyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 留言板，对齐原实现 router/message.js。
 */
@Slf4j
@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final NotifyService notifyService;

    /** 发布留言（1 分钟 5 次） */
    @PostMapping("/add")
    @RateLimit(key = "message:add", max = 5, windowSeconds = 60, message = "留言过于频繁 请稍后再试")
    public Result<Boolean> add(@RequestBody MessageForm form) {
        try {
            Integer userId = Num.toInteger(form.getUserId());
            boolean guest = userId == null || userId == 0;
            String nickName = guest ? messageService.randomGuestNickname() : form.getNickName();
            String message = SensitiveWordFilter.filter(form.getMessage());

            boolean res = messageService.add(form, message, nickName);
            if (!Objects.equals(userId, 1)) {
                notifyService.addNotifyQuietly(1, 3, null,
                        "您收到了来自于：" + nickName + " 的留言: " + message + "！");
            }
            return Result.ok("发布成功", res);
        } catch (Exception e) {
            log.error("发布留言失败", e);
            throw new BizException(ErrorCode.MESSAGE, "发布失败");
        }
    }

    /** 修改留言（1 分钟 5 次 + 登录） */
    @PostMapping("/update")
    @RateLimit(key = "message:update", max = 5, windowSeconds = 60, message = "修改留言过于频繁 请稍后再试")
    @RequireRole(RequireRole.Level.LOGIN)
    public Result<Boolean> update(@RequestBody MessageForm form) {
        try {
            String message = SensitiveWordFilter.filter(form.getMessage());
            return Result.ok("修改成功", messageService.update(form, message));
        } catch (Exception e) {
            log.error("修改留言失败", e);
            throw new BizException(ErrorCode.MESSAGE, "修改失败");
        }
    }

    /** 删除留言（登录，1 分钟 5 次） */
    @PutMapping("/delete")
    @RateLimit(key = "message:delete", max = 5, windowSeconds = 60, message = "删除过于频繁 请稍后再试")
    @RequireRole(RequireRole.Level.LOGIN)
    public Result<Integer> delete(@RequestBody IdListRequest request) {
        try {
            return Result.ok("删除留言成功", messageService.delete(request.getIdList()));
        } catch (Exception e) {
            log.error("删除留言失败", e);
            throw new BizException(ErrorCode.MESSAGE, "删除留言失败");
        }
    }

    /** 后台删除留言（管理员，非超管） */
    @PutMapping("/backDelete")
    @RequireRole(RequireRole.Level.ADMIN_NOT_SUPER)
    public Result<Integer> backDelete(@RequestBody IdListRequest request) {
        try {
            return Result.ok("删除留言成功", messageService.delete(request.getIdList()));
        } catch (Exception e) {
            log.error("删除留言失败", e);
            throw new BizException(ErrorCode.MESSAGE, "删除留言失败");
        }
    }

    /** 留言点赞（1 分钟 5 次） */
    @PutMapping("/like/{id}")
    @RateLimit(key = "message:like", max = 5, windowSeconds = 60, message = "留言点赞过于频繁 请稍后再试")
    public Result<Boolean> like(@PathVariable Integer id) {
        try {
            return Result.ok("留言点赞成功", messageService.like(id));
        } catch (Exception e) {
            log.error("留言点赞失败", e);
            throw new BizException(ErrorCode.MESSAGE, "留言点赞失败");
        }
    }

    /** 取消留言点赞（1 分钟 5 次） */
    @PutMapping("/cancelLike/{id}")
    @RateLimit(key = "message:cancelLike", max = 5, windowSeconds = 60, message = "留言点赞过于频繁 请稍后再试")
    public Result<Boolean> cancelLike(@PathVariable Integer id) {
        try {
            return Result.ok("取消留言点赞成功", messageService.cancelLike(id));
        } catch (Exception e) {
            log.error("取消留言点赞失败", e);
            throw new BizException(ErrorCode.MESSAGE, "取消留言点赞失败");
        }
    }

    /** 分页获取留言（公开） */
    @PostMapping("/getMessageList")
    public Result<CurrentPageResult<MessageView>> getMessageList(@RequestBody MessageQuery query,
                                                                 HttpServletRequest request) {
        try {
            String ip = IpUtil.stripPort(IpUtil.clientIp(request));
            return Result.ok("分页获取留言成功", messageService.list(query, ip));
        } catch (Exception e) {
            log.error("分页获取留言失败", e);
            throw new BizException(ErrorCode.MESSAGE, "分页获取留言失败");
        }
    }

    /** 获取所有留言（公开） */
    @GetMapping("/getAllMessage")
    public Result<Map<String, Object>> getAllMessage() {
        try {
            return Result.ok("获取留言成功", messageService.getAll());
        } catch (Exception e) {
            log.error("获取留言失败", e);
            throw new BizException(ErrorCode.MESSAGE, "获取留言失败");
        }
    }

    /** 热门标签（公开） */
    @GetMapping("/getHotTagList")
    public Result<List<MessageTagCount>> getHotTagList() {
        try {
            return Result.ok("获取留言所有标签成功", messageService.hotTags());
        } catch (Exception e) {
            log.error("获取留言所有标签失败", e);
            throw new BizException(ErrorCode.MESSAGE, "获取留言所有标签失败");
        }
    }
}
