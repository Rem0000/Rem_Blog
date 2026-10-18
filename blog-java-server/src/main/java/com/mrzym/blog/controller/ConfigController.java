package com.mrzym.blog.controller;

import com.mrzym.blog.auth.RequireRole;
import com.mrzym.blog.common.BizException;
import com.mrzym.blog.common.ErrorCode;
import com.mrzym.blog.common.Result;
import com.mrzym.blog.common.Tips;
import com.mrzym.blog.entity.ConfigEntity;
import com.mrzym.blog.limit.RateLimit;
import com.mrzym.blog.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    /**
     * 修改网站设置。原实现还会在 UPLOADTYPE=qiniu|minio 时删除被替换的旧图，
     * 那部分依赖上传模块（批次 5），这里先留空，不影响契约。
     */
    @PostMapping("/update")
    @RequireRole(RequireRole.Level.ADMIN)
    public Result<Boolean> update(@RequestBody ConfigEntity config) {
        try {
            boolean ok = configService.updateConfig(config);
            return Result.ok("修改网站设置成功", ok);
        } catch (Exception e) {
            log.error("修改网站设置失败", e);
            throw new BizException(ErrorCode.CONFIG, "修改网站设置失败");
        }
    }

    /** 获取网站设置：没数据时返回 code=100 的提示 */
    @GetMapping({"", "/"})
    public Object get() {
        try {
            ConfigEntity config = configService.getConfig();
            if (config == null) {
                return Tips.of("请去博客后台完善博客信息");
            }
            return Result.ok("获取网站设置成功", config);
        } catch (Exception e) {
            log.error("获取网站设置失败", e);
            throw new BizException(ErrorCode.CONFIG, "获取网站设置失败");
        }
    }

    /**
     * 访问量 +1。原实现带限流：60 分钟 100 次。
     */
    @PutMapping("/addView")
    @RateLimit(key = "config:addView", max = 100, windowSeconds = 3600,
            message = "访问网站过于频繁 请稍后再试")
    public Object addView() {
        try {
            String res = configService.addView();
            if ("添加成功".equals(res)) {
                return Result.ok("增加访问量成功", res);
            }
            return Tips.of("请先初始化网站信息");
        } catch (Exception e) {
            log.error("增加网站访问量失败", e);
            throw new BizException(ErrorCode.CONFIG, "增加网站访问量失败");
        }
    }
}
