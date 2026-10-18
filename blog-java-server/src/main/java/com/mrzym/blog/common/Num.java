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

    /**
     * 可空版本：null / 空串 / 非数字都返回 null。
     * 用于“传了才更新、没传就跳过”的字段（比如友链的 status）。
     */
    public static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
