package com.mrzym.blog.common;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * 昵称敏感词过滤，对齐原实现 utils/sensitive.js 的行为：
 * 命中敏感词或内嵌脚本关键字时，不再使用原昵称，换一句随机短语。
 *
 * 差异：Node 是去 iciba 的「每日一句」接口取短语（依赖外网），
 * 这里用内置短语兜底，避免注册/改资料被外部接口拖死。
 */
public final class SensitiveWordFilter {

    private static final String[] WORDS = {
            "我是你爸爸", "我是你爸", "我是你爹", "爸爸", "我是你爷爷", "操你奶奶",
            "我是你妈", "我日你爸", "草泥马", "草你妈", "操你妈", "傻逼"
    };

    private static final Pattern BAD_JS = Pattern.compile(
            "script|alert|window|prompt|location|href|iframe|onload|onerror", Pattern.CASE_INSENSITIVE);

    private static final String[] SAYINGS = {
            "生活明朗，万物可爱。",
            "慢慢来，比较快。",
            "今天也要好好生活呀。",
            "保持热爱，奔赴山海。",
            "愿你所求皆如愿。"
    };

    private SensitiveWordFilter() {
    }

    public static String filter(String text) {
        if (text == null) {
            return null;
        }
        boolean hit = BAD_JS.matcher(text).find();
        if (!hit) {
            for (String word : WORDS) {
                if (text.contains(word)) {
                    hit = true;
                    break;
                }
            }
        }
        if (!hit) {
            return text;
        }
        return SAYINGS[ThreadLocalRandom.current().nextInt(SAYINGS.length)];
    }
}
