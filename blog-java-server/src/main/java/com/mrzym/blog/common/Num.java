package com.mrzym.blog.common;

/**
 * 请求体里的数字可能是字符串（前端从表单/query 传过来），统一在这里转。
 */
public final class Num {

    private Num() {
    }

    public static int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
