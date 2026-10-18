package com.mrzym.blog.controller;

import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.common.Result;
import com.mrzym.blog.dto.UserInfo;
import com.mrzym.blog.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 博主信息，只回公开字段（不含 username / password） */
    @GetMapping("/getUserInfoById/{id}")
    public Result<UserInfo> getUserInfoById(@PathVariable Integer id) {
        try {
            return Result.ok("获取用户信息成功", userService.getInfoById(id));
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
            throw new BizException(ErrorCode.USER, "获取用户信息失败");
        }
    }
}
