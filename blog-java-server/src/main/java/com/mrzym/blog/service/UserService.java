package com.mrzym.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mrzym.blog.auth.JwtUtil;
import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.CurrentPageResult;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.common.IpUtil;
import com.mrzym.blog.common.Num;
import com.mrzym.blog.common.SensitiveWordFilter;
import com.mrzym.blog.config.BlogProperties;
import com.mrzym.blog.dto.AdminUpdateUserInfoForm;
import com.mrzym.blog.dto.LoginForm;
import com.mrzym.blog.dto.RegisterForm;
import com.mrzym.blog.dto.UpdateOwnUserInfoForm;
import com.mrzym.blog.dto.UpdatePasswordForm;
import com.mrzym.blog.dto.UserInfo;
import com.mrzym.blog.dto.UserListItem;
import com.mrzym.blog.dto.UserListQuery;
import com.mrzym.blog.entity.UserEntity;
import com.mrzym.blog.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 用户模块，对齐原实现 middleware/user + controller/user + service/user。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    /** 与 Node 里写死的默认头像一致 */
    private static final String DEFAULT_AVATAR = "http://mrzym.top/online/9bb507f4bd065759a3d093d04.webp";

    /** 原实现里虚拟超管 id */
    public static final int SUPER_ADMIN_ID = 5201314;

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BlogProperties blogProperties;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 查不到时返回 null，与原实现一致（success + result:null，不报错） */
    public UserInfo getInfoById(Integer id) {
        UserInfo info = userMapper.selectInfoById(id);
        if (info != null) {
            // 库里存的是原始 IP，展示时转属地（对齐原实现的 getIpAddress）
            info.setIpAddress(IpUtil.locate(info.getIpAddress()));
        }
        return info;
    }

    public Map<String, Object> getSuperAdminInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", SUPER_ADMIN_ID);
        info.put("role", 1);
        info.put("nick_name", "超级管理员");
        return info;
    }

    // ==================== 注册 / 登录 ====================

    public Map<String, Object> register(RegisterForm form, String ip) {
        String username = form.getUsername();
        if ("admin".equals(username)) {
            throw new BizException(ErrorCode.USER, "admin账号已存在");
        }
        if (userMapper.selectByUsername(username) != null) {
            throw new BizException(ErrorCode.USER, "用户名已经存在");
        }

        String nickName = SensitiveWordFilter.filter(form.getNickName());
        if (nickName == null || nickName.isEmpty()) {
            nickName = randomNickname("Rem的迷弟");
        }
        LocalDateTime now = LocalDateTime.now().withNano(0);

        UserEntity entity = new UserEntity();
        entity.setUsername(username);
        entity.setPassword(passwordEncoder.encode(form.getPassword()));
        entity.setRole(2);
        entity.setNickName(nickName);
        entity.setQq(form.getQq() == null ? "" : form.getQq());
        entity.setAvatar(DEFAULT_AVATAR);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        userMapper.insert(entity);

        updateIp(entity.getId(), ip);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entity.getId());
        result.put("username", entity.getUsername());
        return result;
    }

    public Map<String, Object> login(LoginForm form, String ip) {
        String username = form.getUsername();

        if ("admin".equals(username)) {
            String adminPassword = blogProperties.getAdminPassword();
            if (adminPassword == null || adminPassword.isEmpty()) {
                throw new BizException(ErrorCode.USER, "请在env配置文件里添加超级管理员密码");
            }
            if (!adminPassword.equals(form.getPassword())) {
                throw new BizException(ErrorCode.USER, "密码错误");
            }
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("nick_name", "超级管理员");
            claims.put("id", SUPER_ADMIN_ID);
            claims.put("role", 1);
            claims.put("username", "admin");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("token", jwtUtil.sign(claims));
            result.put("username", "超级管理员");
            result.put("role", 1);
            result.put("id", SUPER_ADMIN_ID);
            return result;
        }

        UserEntity user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BizException(ErrorCode.USER, "用户名不存在");
        }
        if (!passwordEncoder.matches(form.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.USER, "密码不匹配");
        }

        updateIp(user.getId(), ip);
        String ipAddress = IpUtil.locate(ip);

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("id", user.getId());
        claims.put("role", user.getRole());
        claims.put("username", user.getUsername());
        claims.put("nick_name", user.getNickName());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", jwtUtil.sign(claims));
        result.put("username", user.getUsername());
        result.put("role", user.getRole());
        result.put("id", user.getId());
        result.put("ipAddress", ipAddress);
        return result;
    }

    // ==================== 资料 / 密码 / 角色 ====================

    public boolean updateOwnUserInfo(Integer userId, UpdateOwnUserInfoForm form) {
        UserEntity one = userMapper.selectById(userId);
        if (one != null && one.getAvatar() != null && !one.getAvatar().isEmpty()
                && !one.getAvatar().equals(form.getAvatar())) {
            // TODO 上传模块：qiniu/minio 模式下要删除旧头像，依赖 utils 上传实现
            log.debug("跳过删除旧头像: {}", one.getAvatar());
        }

        String nickName = SensitiveWordFilter.filter(form.getNickName());
        LocalDateTime now = LocalDateTime.now().withNano(0);
        UserEntity patch = new UserEntity();
        patch.setId(userId);
        patch.setNickName(nickName);
        patch.setAvatar(form.getAvatar());
        patch.setQq(form.getQq());
        patch.setUpdatedAt(now);
        return userMapper.updateById(patch) > 0;
    }

    public boolean updatePassword(Integer userId, String username, UpdatePasswordForm form) {
        if (userId != null && userId == 2) {
            throw new BizException(ErrorCode.USER, "测试用户密码不可以修改哦");
        }
        if (form.getPassword1() == null || !form.getPassword1().equals(form.getPassword2())) {
            throw new BizException(ErrorCode.USER, "两次输入密码不一致");
        }
        UserEntity user = userMapper.selectByUsername(username);
        if (user == null || form.getPassword() == null
                || !passwordEncoder.matches(form.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.USER, "密码不匹配");
        }

        LocalDateTime now = LocalDateTime.now().withNano(0);
        UserEntity patch = new UserEntity();
        patch.setId(userId);
        patch.setPassword(passwordEncoder.encode(form.getPassword1()));
        patch.setUpdatedAt(now);
        return userMapper.updateById(patch) > 0;
    }

    public boolean updateRole(Integer id, Integer role) {
        UserEntity patch = new UserEntity();
        patch.setId(id);
        patch.setRole(role);
        patch.setUpdatedAt(LocalDateTime.now().withNano(0));
        return userMapper.updateById(patch) > 0;
    }

    public boolean adminUpdateUserInfo(AdminUpdateUserInfoForm form) {
        Integer id = Num.toInteger(form.getId());
        UserEntity one = id == null ? null : userMapper.selectById(id);
        if (one != null && one.getAvatar() != null && !one.getAvatar().isEmpty()
                && !one.getAvatar().equals(form.getAvatar())) {
            // TODO 上传模块：qiniu/minio 模式下要删除旧头像
            log.debug("跳过删除旧头像: {}", one.getAvatar());
        }

        UserEntity patch = new UserEntity();
        patch.setId(id);
        patch.setNickName(form.getNickName());
        patch.setAvatar(form.getAvatar());
        patch.setUpdatedAt(LocalDateTime.now().withNano(0));
        return userMapper.updateById(patch) > 0;
    }

    public CurrentPageResult<UserListItem> getUserList(UserListQuery query) {
        int pageNo = Math.max(Num.toInt(query.getCurrent(), 1), 1);
        int pageSize = Num.toInt(query.getSize(), 10);
        if (pageSize <= 0) {
            pageSize = 10;
        }

        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(UserEntity::getId, UserEntity::getUsername, UserEntity::getRole,
                UserEntity::getNickName, UserEntity::getQq, UserEntity::getIp,
                UserEntity::getAvatar, UserEntity::getCreatedAt, UserEntity::getUpdatedAt);
        if (query.getNickName() != null && !query.getNickName().isBlank()) {
            wrapper.like(UserEntity::getNickName, query.getNickName());
        }
        // 原实现只在 role 是数字且非 0 时过滤
        if (query.getRole() instanceof Number number && number.intValue() != 0) {
            wrapper.eq(UserEntity::getRole, number.intValue());
        }

        Page<UserEntity> page = userMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<UserListItem> list = page.getRecords().stream().map(entity -> {
            UserListItem item = new UserListItem();
            item.setId(entity.getId());
            item.setUsername(entity.getUsername());
            item.setRole(entity.getRole());
            item.setNickName(entity.getNickName());
            item.setQq(entity.getQq());
            item.setIp(entity.getIp());
            item.setAvatar(entity.getAvatar());
            item.setCreatedAt(entity.getCreatedAt());
            item.setUpdatedAt(entity.getUpdatedAt());
            item.setIpAddress(entity.getIp() == null || entity.getIp().isBlank()
                    ? "火星" : IpUtil.locate(entity.getIp()));
            return item;
        }).toList();

        return CurrentPageResult.of(
                query.getCurrent() != null ? query.getCurrent() : pageNo,
                query.getSize() != null ? query.getSize() : pageSize,
                page.getTotal(),
                list);
    }

    // ==================== 内部 ====================

    private void updateIp(Integer id, String ip) {
        if (id == null || ip == null) {
            return;
        }
        userMapper.update(null, new LambdaUpdateWrapper<UserEntity>()
                .set(UserEntity::getIp, ip)
                .eq(UserEntity::getId, id));
    }

    private String randomNickname(String prefix) {
        String chars = "0123456789abcdefghijklmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder(prefix);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
