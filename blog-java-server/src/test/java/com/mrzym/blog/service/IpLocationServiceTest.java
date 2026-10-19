package com.mrzym.blog.service;

import net.dreamlu.mica.ip2region.config.Ip2regionProperties;
import net.dreamlu.mica.ip2region.impl.Ip2regionSearcherImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IpLocationServiceTest {

    private static Ip2regionSearcherImpl searcher;
    private static IpLocationService service;

    @BeforeAll
    static void setUp() throws Exception {
        searcher = new Ip2regionSearcherImpl(new DefaultResourceLoader(), new Ip2regionProperties());
        searcher.afterPropertiesSet();
        service = new IpLocationService(searcher);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (searcher != null) {
            searcher.destroy();
        }
    }

    @Test
    void locateKnownIps() {
        assertEquals("江苏", service.locate("180.109.81.135"));
        assertEquals("美国", service.locate("8.8.8.8"));
        assertEquals("广东", service.locate("240e:3b7:3272:d8d0:db09:c067:8d59:539e"));
        assertEquals("本机地址", service.locate("127.0.0.1"));
        assertEquals("本机地址", service.locate("192.168.1.1"));
    }

    @Test
    void nonIpTextIsReturnedAsIs() {
        assertEquals("四川", service.locate("四川"));
        assertEquals("本机地址", service.locate(null));
        assertEquals("本机地址", service.locate(" "));
    }
}
