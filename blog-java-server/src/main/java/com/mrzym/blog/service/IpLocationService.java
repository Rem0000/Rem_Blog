package com.mrzym.blog.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.ip2region.core.Ip2regionSearcher;
import net.dreamlu.mica.ip2region.core.IpInfo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * IP -> 属地，基于离线库 ip2region（mica-ip2region 自带 v4/v6 数据，无需外网）。
 *
 * <p>对齐原 Node 的 {@code getIpAddress}：
 * <ul>
 *   <li>中国 IP 返回省份，如「江苏」「广东」「北京」；</li>
 *   <li>省份取不到时返回国家，如「美国」；</li>
 *   <li>内网/回环/解析失败返回「本机地址」；</li>
 *   <li>历史数据里已经是属地文本（如「四川」）的原样返回。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IpLocationService {

    private static final String LOCAL = "本机地址";
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private final Ip2regionSearcher searcher;

    public String locate(String ipOrRegion) {
        if (!StringUtils.hasText(ipOrRegion)) {
            return LOCAL;
        }
        String value = ipOrRegion.trim();
        if (!isIp(value)) {
            // 历史数据里直接存的是「四川」这种属地文本，保持原样
            return value;
        }
        if (isLocalIp(value)) {
            return LOCAL;
        }

        try {
            IpInfo info = searcher.memorySearch(value);
            if (info == null) {
                return LOCAL;
            }
            String province = normalizeProvince(info.getProvince());
            if (StringUtils.hasText(province)) {
                return province;
            }
            String country = normalizeCountry(info.getCountry());
            return StringUtils.hasText(country) ? country : LOCAL;
        } catch (Exception e) {
            log.debug("IP 属地解析失败: ip={}", value, e);
            return LOCAL;
        }
    }

    private static boolean isIp(String value) {
        if (IPV4.matcher(value).matches()) {
            return true;
        }
        if (!value.contains(":")) {
            return false;
        }
        try {
            InetAddress.getByName(value);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static boolean isLocalIp(String value) {
        try {
            InetAddress address = InetAddress.getByName(value);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * ip2region 返回「江苏省」「北京市」「广西壮族自治区」「香港特别行政区」，
     * 原 Node 的 xz-ipnet 返回「江苏」「北京」「广西」「香港」，这里做同样的缩写。
     */
    private static String normalizeProvince(String province) {
        if (!StringUtils.hasText(province)) {
            return null;
        }
        String value = province.trim();
        if (isUnknown(value)) {
            return null;
        }
        if (value.endsWith("特别行政区")) {
            return value.substring(0, value.length() - 5);
        }
        if (value.endsWith("自治区")) {
            value = value.substring(0, value.length() - 3);
            if (value.endsWith("壮族") || value.endsWith("回族") || value.endsWith("维吾尔")) {
                value = value.substring(0, value.length() - 2);
            }
            return value;
        }
        if (value.endsWith("省") || value.endsWith("市")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String normalizeCountry(String country) {
        if (!StringUtils.hasText(country)) {
            return null;
        }
        String value = country.trim();
        return isUnknown(value) ? null : value;
    }

    private static boolean isUnknown(String value) {
        return value.isEmpty()
                || "0".equals(value)
                || "未知".equals(value)
                || "本机".equals(value)
                || "本机地址".equals(value)
                || "内网IP".equals(value)
                || "局域网".equals(value);
    }
}
