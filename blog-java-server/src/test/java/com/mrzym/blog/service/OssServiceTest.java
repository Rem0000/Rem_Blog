package com.mrzym.blog.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.PutObjectRequest;
import com.mrzym.blog.common.BizException;
import com.mrzym.blog.config.BlogProperties;
import com.mrzym.blog.config.OssProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class OssServiceTest {

    @Test
    void uploadWithoutConfigFailsWithClearMessage() {
        OssService service = new OssService(new OssProperties());

        BizException exception = assertThrows(BizException.class, () -> service.upload(image()));

        assertEquals("100006", exception.getCode());
        assertTrue(exception.getMessage().contains("oss.access-key-id"), exception.getMessage());
        assertTrue(exception.getMessage().contains("oss.bucket-name"), exception.getMessage());
    }

    @Test
    void uploadToOssBuildsPublicUrlAndSetsObjectMetadata() {
        OssProperties properties = configuredProperties();
        properties.setDir("blog/images/");

        OSS oss = mock(OSS.class);
        OssService service = new OssService(properties, oss);

        String url = service.upload(image());

        assertTrue(url.startsWith("https://demo-bucket.oss-cn-shanghai.aliyuncs.com/blog/images/"), url);
        assertTrue(url.endsWith(".png"), url);

        PutObjectRequest request = captureRequest(oss);
        assertEquals("demo-bucket", request.getBucketName());
        assertTrue(request.getKey().startsWith("blog/images/"), request.getKey());
        assertEquals("image/png", request.getMetadata().getContentType());
        assertEquals("public, max-age=31536000", request.getMetadata().getCacheControl());
        assertEquals("public-read",
                request.getMetadata().getRawMetadata().get("x-oss-object-acl"));
    }

    @Test
    void configuredRefererTakesPrecedenceOverRequestReferer() {
        OssProperties properties = configuredProperties();
        properties.setReferer("https://mrzym.top/");

        OSS oss = mock(OSS.class);
        OssService service = new OssService(properties, oss);

        service.upload(image(), "http://localhost:8080");

        assertEquals("https://mrzym.top/", captureRequest(oss).getHeaders().get("Referer"));
    }

    @Test
    void uploadFallsBackToRequestReferer() {
        OssProperties properties = configuredProperties();

        OSS oss = mock(OSS.class);
        OssService service = new OssService(properties, oss);

        service.upload(image(), "http://localhost:8080");

        assertEquals("http://localhost:8080", captureRequest(oss).getHeaders().get("Referer"));
    }

    @Test
    void uploadWithoutAnyRefererDoesNotSendHeader() {
        OssProperties properties = configuredProperties();

        OSS oss = mock(OSS.class);
        OssService service = new OssService(properties, oss);

        service.upload(image());

        assertNull(captureRequest(oss).getHeaders().get("Referer"));
    }

    @Test
    void deleteOnlyOwnOssImagesUnderConfiguredPrefix() {
        OSS oss = mock(OSS.class);
        OssService service = new OssService(configuredProperties(), oss);

        int deleted = service.deleteByUrls(List.of(
                "https://demo-bucket.oss-cn-shanghai.aliyuncs.com/images/a.jpg?x=1",
                "https://demo-bucket.oss-cn-shanghai.aliyuncs.com/other/b.jpg",
                "https://mrzym.top/online/a.webp",
                "https://other.example.com/images/a.jpg",
                "https://demo-bucket.oss-cn-shanghai.aliyuncs.com/images/a.jpg"));

        assertEquals(1, deleted);
        verify(oss).deleteObject("demo-bucket", "images/a.jpg");
        verifyNoMoreInteractions(oss);
    }

    @Test
    void deleteSupportsCustomDomainWithPath() {
        OssProperties properties = configuredProperties();
        properties.setDomain("https://cdn.example.com/blog/");
        OSS oss = mock(OSS.class);
        OssService service = new OssService(properties, oss);

        assertTrue(service.deleteByUrl("https://cdn.example.com/blog/images/b.jpg"));

        verify(oss).deleteObject("demo-bucket", "images/b.jpg");
    }

    @Test
    void deleteDecodesUrlEncodedObjectKey() {
        OSS oss = mock(OSS.class);
        OssService service = new OssService(configuredProperties(), oss);

        assertTrue(service.deleteByUrl(
                "https://demo-bucket.oss-cn-shanghai.aliyuncs.com/images/%E4%B8%AD%E6%96%87.jpg"));

        verify(oss).deleteObject("demo-bucket", "images/中文.jpg");
    }

    @Test
    void deleteSkippedWhenUploadTypeIsNotOss() {
        OSS oss = mock(OSS.class);
        BlogProperties blog = new BlogProperties();
        blog.setUploadType("local");
        OssService service = new OssService(configuredProperties(), blog);

        assertFalse(service.deleteByUrl(
                "https://demo-bucket.oss-cn-shanghai.aliyuncs.com/images/a.jpg"));
        verifyNoMoreInteractions(oss);
    }

    @Test
    void deleteWithoutConfigIsSkipped() {
        OSS oss = mock(OSS.class);
        OssService service = new OssService(new OssProperties(), oss);

        assertFalse(service.deleteByUrl(
                "https://demo-bucket.oss-cn-shanghai.aliyuncs.com/images/a.jpg"));
        verifyNoMoreInteractions(oss);
    }

    private static OssProperties configuredProperties() {
        OssProperties properties = new OssProperties();
        properties.setEndpoint("https://oss-cn-shanghai.aliyuncs.com");
        properties.setBucketName("demo-bucket");
        properties.setAccessKeyId("id");
        properties.setAccessKeySecret("secret");
        return properties;
    }

    private static MockMultipartFile image() {
        return new MockMultipartFile("file", "Cover.PNG", "image/png", new byte[]{1, 2, 3});
    }

    private static PutObjectRequest captureRequest(OSS oss) {
        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(oss).putObject(captor.capture());
        return captor.getValue();
    }
}
