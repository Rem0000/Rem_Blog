package com.mrzym.blog.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.mrzym.blog.entity.PhotoEntity;
import com.mrzym.blog.mapper.PhotoMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PhotoServiceTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PhotoEntity.class);
    }

    @Test
    void permanentDeleteRemovesOssImage() {
        PhotoMapper mapper = mock(PhotoMapper.class);
        OssService oss = mock(OssService.class);
        PhotoService service = new PhotoService(mapper, oss);

        PhotoEntity photo = new PhotoEntity();
        photo.setId(1);
        photo.setUrl("https://demo-bucket.oss-cn-shanghai.aliyuncs.com/images/a.jpg");

        when(mapper.selectBatchIds(List.of(1))).thenReturn(List.of(photo));
        when(mapper.deleteBatchIds(List.of(1))).thenReturn(1);
        doReturn(List.of()).when(mapper).selectList(any());

        assertEquals(1, service.delete(List.of(1), 2));

        verify(oss).deleteByUrl(photo.getUrl());
    }

    @Test
    void recycleDoesNotTouchOss() {
        PhotoMapper mapper = mock(PhotoMapper.class);
        OssService oss = mock(OssService.class);
        PhotoService service = new PhotoService(mapper, oss);

        when(mapper.update(any(), any())).thenReturn(1);

        assertEquals(List.of(1), service.delete(List.of(1), 1));

        verifyNoInteractions(oss);
    }
}
