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

    /** 取网站设置（表里只应有一行） */
    public ConfigEntity getConfig() {
        List<ConfigEntity> list = configMapper.selectList(
                new LambdaQueryWrapper<ConfigEntity>().last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 有 id 且存在则更新，否则插入（对齐原实现逻辑） */
    public boolean updateConfig(ConfigEntity config) {
        Integer id = config.getId();
        if (id != null && configMapper.selectById(id) != null) {
            return configMapper.updateById(config) > 0;
        }
        return configMapper.insert(config) > 0;
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
