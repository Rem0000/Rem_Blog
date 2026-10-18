package com.mrzym.blog.controller;

import com.mrzym.blog.auth.RequireRole;
import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.common.MessageOnlyResult;
import com.mrzym.blog.common.Result;
import com.mrzym.blog.dto.ChatForm;
import com.mrzym.blog.dto.ChatPageResult;
import com.mrzym.blog.dto.ChatQuery;
import com.mrzym.blog.entity.ChatEntity;
import com.mrzym.blog.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 聊天，对齐原实现 router/chat.js。
 * 注意：删除类接口原实现只传了 message（result 是 undefined，响应里没有 result 字段）。
 */
@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** 新增聊天（登录） */
    @PostMapping("/add")
    @RequireRole(RequireRole.Level.LOGIN)
    public Result<Map<String, Object>> add(@RequestBody ChatForm form) {
        try {
            ChatEntity chat = chatService.create(form);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("content", chat.getContent());
            return Result.ok("新增聊天成功", data);
        } catch (Exception e) {
            log.error("新增聊天失败", e);
            throw new BizException(ErrorCode.CHAT, "新增聊天失败");
        }
    }

    /** 撤回单条（公开，原实现没挂 auth） */
    @DeleteMapping("/deleteOne/{id}")
    public MessageOnlyResult deleteOne(@PathVariable Integer id) {
        try {
            chatService.deleteOne(id);
            return MessageOnlyResult.of("撤回聊天成功");
        } catch (Exception e) {
            log.error("撤回聊天失败", e);
            throw new BizException(ErrorCode.CHAT, "撤回聊天失败");
        }
    }

    /** 清空聊天（管理员，TRUNCATE 会重置自增） */
    @PostMapping("/delete")
    @RequireRole(RequireRole.Level.ADMIN)
    public MessageOnlyResult deleteAll() {
        try {
            chatService.deleteAll();
            return MessageOnlyResult.of("删除聊天成功");
        } catch (Exception e) {
            log.error("删除聊天失败", e);
            throw new BizException(ErrorCode.CHAT, "删除聊天失败");
        }
    }

    /** 条件分页（公开） */
    @PostMapping("/getChatList")
    public Result<ChatPageResult> getChatList(@RequestBody ChatQuery query) {
        try {
            return Result.ok("分页查找聊天成功", chatService.getChatList(query));
        } catch (Exception e) {
            log.error("分页查找聊天失败", e);
            throw new BizException(ErrorCode.CHAT, "分页查找聊天失败");
        }
    }
}
