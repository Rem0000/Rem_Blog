package com.mrzym.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mrzym.blog.entity.ConfigEntity;
import com.mrzym.blog.mapper.ConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConfigService {

    private final ConfigMapper configMapper;
    private final OssService ossService;

    /** 取网站设置（表里只应有一行） */
    public ConfigEntity getConfig() {
        List<ConfigEntity> list = configMapper.selectList(
                new LambdaQueryWrapper<ConfigEntity>().last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 有 id 且存在则更新，否则插入（对齐原实现逻辑） */
    public boolean updateConfig(ConfigEntity config) {
        Integer id = config.getId();
        ConfigEntity old = id == null ? null : configMapper.selectById(id);
        boolean updated;
        if (old != null) {
            updated = configMapper.updateById(config) > 0;
        } else {
            updated = configMapper.insert(config) > 0;
        }
        if (updated && old != null) {
            // 原实现只在 qiniu/minio 下删旧图；现在 OSS 模式也做，外部 URL 会被 OssService 跳过
            deleteReplacedImage(old.getAvatarBg(), config.getAvatarBg());
            deleteReplacedImage(old.getBlogAvatar(), config.getBlogAvatar());
            deleteReplacedImage(old.getQqLink(), config.getQqLink());
            deleteReplacedImage(old.getWeChatLink(), config.getWeChatLink());
            deleteReplacedImage(old.getWeChatGroup(), config.getWeChatGroup());
            deleteReplacedImage(old.getQqGroup(), config.getQqGroup());
            deleteReplacedImage(old.getWeChatPay(), config.getWeChatPay());
            deleteReplacedImage(old.getAliPay(), config.getAliPay());
        }
        return updated;
    }

    private void deleteReplacedImage(String oldUrl, String newUrl) {
        if (oldUrl == null || oldUrl.isBlank() || oldUrl.equals(newUrl)
                || newUrl == null || newUrl.isBlank()) {
            return;
        }
        ossService.deleteByUrl(oldUrl);
    }

    /** 访问量 +1，返回原实现里的中文标记 */
    public String addView() {
        ConfigEntity config = getConfig();
        if (config == null) {
            return "需要初始化";
        }
        configMapper.update(null, new LambdaUpdateWrapper<ConfigEntity>()
                .eq(ConfigEntity::getId, config.getId())
                .setSql("view_time = view_time + 1"));
        return "添加成功";
    }
}
