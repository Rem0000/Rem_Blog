package com.mrzym.blog.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IpUtilTest {

    @Test
    void stripPortKeepsIpv4AndIpv6Correct() {
        assertEquals("127.0.0.1", IpUtil.stripPort("127.0.0.1:8080"));
        assertEquals("127.0.0.1", IpUtil.stripPort("127.0.0.1"));
        assertEquals("::1", IpUtil.stripPort("[::1]:8080"));
        assertEquals("::1", IpUtil.stripPort("::1"));
        assertEquals("2001:db8::1", IpUtil.stripPort("2001:db8::1"));
        assertNull(IpUtil.stripPort(null));
    }
}
