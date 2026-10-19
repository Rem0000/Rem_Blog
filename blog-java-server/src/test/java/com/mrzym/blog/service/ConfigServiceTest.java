package com.mrzym.blog.service;

import com.mrzym.blog.entity.ConfigEntity;
import com.mrzym.blog.mapper.ConfigMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigServiceTest {

    @Test
    void updateDeletesOnlyReplacedOssImages() {
        ConfigMapper mapper = mock(ConfigMapper.class);
        OssService oss = mock(OssService.class);
        ConfigService service = new ConfigService(mapper, oss);

        ConfigEntity old = new ConfigEntity();
        old.setId(1);
        old.setAvatarBg("https://demo-bucket.oss-cn-shanghai.aliyuncs.com/images/old-bg.jpg");
        old.setBlogAvatar("https://demo-bucket.oss-cn-shanghai.aliyuncs.com/images/old-avatar.jpg");
        old.setQqLink("https://demo-bucket.oss-cn-shanghai.aliyuncs.com/images/old-qq.jpg");
        old.setAliPay("https://demo-bucket.oss-cn-shanghai.aliyuncs.com/images/old-ali.jpg");

        ConfigEntity form = new ConfigEntity();
        form.setId(1);
        form.setAvatarBg("https://demo-bucket.oss-cn-shanghai.aliyuncs.com/images/new-bg.jpg");
        form.setBlogAvatar(old.getBlogAvatar()); // 没变，不应删
        form.setQqLink("https://demo-bucket.oss-cn-shanghai.aliyuncs.com/images/new-qq.jpg");
        form.setAliPay(""); // 被清空，原实现也不删

        when(mapper.selectById(1)).thenReturn(old);
        when(mapper.updateById(any(ConfigEntity.class))).thenReturn(1);

        assertTrue(service.updateConfig(form));

        verify(oss).deleteByUrl(old.getAvatarBg());
        verify(oss).deleteByUrl(old.getQqLink());
        verify(oss, never()).deleteByUrl(old.getBlogAvatar());
        verify(oss, never()).deleteByUrl(old.getAliPay());
    }
}
