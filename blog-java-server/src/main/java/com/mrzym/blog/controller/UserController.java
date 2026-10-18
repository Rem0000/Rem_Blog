package com.mrzym.blog.controller;

import com.mrzym.blog.auth.RequireRole;
import com.mrzym.blog.auth.UserContext;
import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.CurrentPageResult;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.common.IpUtil;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.common.Result;
import com.mrzym.blog.dto.AdminUpdateUserInfoForm;
import com.mrzym.blog.dto.LoginForm;
import com.mrzym.blog.dto.RegisterForm;
import com.mrzym.blog.dto.UpdateOwnUserInfoForm;
import com.mrzym.blog.dto.UpdatePasswordForm;
import com.mrzym.blog.dto.UserInfo;
import com.mrzym.blog.dto.UserListItem;
import com.mrzym.blog.dto.UserListQuery;
import com.mrzym.blog.limit.RateLimit;
import com.mrzym.blog.service.UserService;
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

/**
 * 用户模块，对齐原实现 router/user.js。
 */
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 博主信息：普通用户只回公开字段；虚拟超管 5201314 只回 id/role/nick_name */
    @GetMapping("/getUserInfoById/{id}")
    public Result<Object> getUserInfoById(@PathVariable Integer id) {
        try {
            if (id == UserService.SUPER_ADMIN_ID) {
                return Result.ok("获取用户信息成功", userService.getSuperAdminInfo());
            }
            UserInfo info = userService.getInfoById(id);
            return Result.ok("获取用户信息成功", info);
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
            throw new BizException(ErrorCode.USER, "获取用户信息失败");
        }
    }

    /** 注册（1 分钟 3 次） */
    @PostMapping("/register")
    @RateLimit(key = "user:register", max = 3, windowSeconds = 60, message = "用户注册过于频繁 请稍后再试")
    public Result<Object> register(@RequestBody RegisterForm form, HttpServletRequest request) {
        verifyCredentials(form.getUsername(), form.getPassword());
        String ip = ipOf(request);
        try {
            return Result.ok("用户注册成功", userService.register(form, ip));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("用户注册失败", e);
            throw new BizException(ErrorCode.USER, "用户注册失败");
        }
    }

    /** 登录 */
    @PostMapping("/login")
    public Result<Object> login(@RequestBody LoginForm form, HttpServletRequest request) {
        verifyCredentials(form.getUsername(), form.getPassword());
        String ip = ipOf(request);
        try {
            String message = "admin".equals(form.getUsername()) ? "超级管理员登录成功" : "用户登录成功";
            return Result.ok(message, userService.login(form, ip));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("用户登陆失败", e);
            throw new BizException(ErrorCode.USER, "用户登陆失败");
        }
    }

    /** 修改自己的资料（登录 + 非超管） */
    @PutMapping("/updateOwnUserInfo")
    @RequireRole(RequireRole.Level.NOT_SUPER)
    public Result<Boolean> updateOwnUserInfo(@RequestBody UpdateOwnUserInfoForm form) {
        try {
            Integer userId = currentUserId();
            return Result.ok("修改用户成功", userService.updateOwnUserInfo(userId, form));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("修改用户失败", e);
            throw new BizException(ErrorCode.USER, "修改用户失败");
        }
    }

    /** 修改密码（登录 + 非超管） */
    @PutMapping("/updatePassword")
    @RequireRole(RequireRole.Level.NOT_SUPER)
    public Result<Boolean> updatePassword(@RequestBody UpdatePasswordForm form) {
        try {
            return Result.ok("修改用户密码成功",
                    userService.updatePassword(currentUserId(), UserContext.username(), form));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("修改用户密码失败", e);
            throw new BizException(ErrorCode.USER, "修改用户密码失败");
        }
    }

    /** 管理员改角色 */
    @PutMapping("/updateRole/{id}/{role}")
    @RequireRole(RequireRole.Level.ADMIN)
    public Result<Boolean> updateRole(@PathVariable Integer id, @PathVariable Integer role) {
        try {
            return Result.ok("修改角色成功", userService.updateRole(id, role));
        } catch (Exception e) {
            log.error("修改角色失败", e);
            throw new BizException(ErrorCode.USER, "修改角色失败");
        }
    }

    /** 管理员改用户信息 */
    @PutMapping("/adminUpdateUserInfo")
    @RequireRole(RequireRole.Level.ADMIN)
    public Result<Boolean> adminUpdateUserInfo(@RequestBody AdminUpdateUserInfoForm form) {
        try {
            return Result.ok("修改用户信息成功", userService.adminUpdateUserInfo(form));
        } catch (Exception e) {
            log.error("修改用户信息失败", e);
            throw new BizException(ErrorCode.USER, "修改用户信息失败");
        }
    }

    /** 分页获取用户列表（登录即可） */
    @PostMapping("/getUserList")
    @RequireRole(RequireRole.Level.LOGIN)
    public Result<CurrentPageResult<UserListItem>> getUserList(@RequestBody UserListQuery query) {
        try {
            return Result.ok("分页获取用户列表成功", userService.getUserList(query));
        } catch (Exception e) {
            log.error("分页获取用户列表失败", e);
            throw new BizException(ErrorCode.USER, "分页获取用户列表失败");
        }
    }

    // ==================== 内部 ====================

    /** 对应 middleware/user 的 userValidate */
    private void verifyCredentials(String username, String password) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            throw new BizException(ErrorCode.USER, "用户名或密码为空");
        }
        if (!username.matches("[A-Za-z0-9]+")) {
            throw new BizException(ErrorCode.USER, "用户名只能是数字和字母组成");
        }
    }

    private String ipOf(HttpServletRequest request) {
        return IpUtil.stripPort(IpUtil.clientIp(request));
    }

    private Integer currentUserId() {
        Long id = UserContext.id();
        return id == null ? null : Num.toInteger(id);
    }
}
