package com.mrzym.blog.controller;

import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.common.Result;
import com.mrzym.blog.dto.PageHeaderItem;
import com.mrzym.blog.service.PageHeaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
