package com.mrzym.blog.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UploadControllerTest {

    @Test
    void originTakesPrecedence() {
        assertEquals("https://blog.example.com",
                UploadController.chooseReferer("https://blog.example.com", "https://blog.example.com/article/1"));
    }

    @Test
    void refererIsTrimmedToOrigin() {
        assertEquals("https://blog.example.com",
                UploadController.chooseReferer(null, "https://blog.example.com/article/1?x=1"));
    }

    @Test
    void nonDefaultPortIsKept() {
        assertEquals("http://localhost:8080",
                UploadController.chooseReferer(null, "http://localhost:8080/admin/"));
    }

    @Test
    void defaultPortIsDropped() {
        assertEquals("https://blog.example.com",
                UploadController.chooseReferer(null, "https://blog.example.com:443/article"));
    }

    @Test
    void nullOriginStringIsIgnored() {
        assertNull(UploadController.chooseReferer("null", null));
        assertNull(UploadController.chooseReferer(null, null));
    }
}
