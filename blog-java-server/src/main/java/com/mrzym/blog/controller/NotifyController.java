package com.mrzym.blog.controller;

import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.CurrentPageResult;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.common.Result;
import com.mrzym.blog.dto.NotifyQuery;
import com.mrzym.blog.entity.NotifyEntity;
import com.mrzym.blog.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 消息通知，对齐原实现 router/notify.js。
 * 注意删除也是 PUT（原实现如此）。
 */
@Slf4j
@RestController
@RequestMapping("/notify")
@RequiredArgsConstructor
public class NotifyController {

    private final NotifyService notifyService;

    @PutMapping("/update/{id}")
    public Result<Boolean> update(@PathVariable Integer id) {
        try {
            return Result.ok("已阅消息通知成功", notifyService.markViewed(id));
        } catch (Exception e) {
            log.error("已阅消息通知失败", e);
            throw new BizException(ErrorCode.NOTIFY, "已阅消息通知失败");
        }
    }

    @PutMapping("/delete/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Integer id) {
        try {
            int affected = notifyService.deleteNotify(id);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("res", affected);
            return Result.ok("删除消息通知成功", data);
        } catch (Exception e) {
            log.error("删除消息通知失败", e);
            throw new BizException(ErrorCode.NOTIFY, "删除消息通知失败");
        }
    }

    @PostMapping("/getNotifyList")
    public Result<CurrentPageResult<NotifyEntity>> getNotifyList(@RequestBody NotifyQuery query) {
        try {
            return Result.ok("分页查找消息通知成功", notifyService.getNotifyList(query));
        } catch (Exception e) {
            log.error("分页查询消息通知失败", e);
            throw new BizException(ErrorCode.NOTIFY, "分页查询消息通知失败");
        }
    }
}
