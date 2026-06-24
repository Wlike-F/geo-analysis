package com.xy.welllog.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 通用工具类
 */
public class CommonUtils {

    public static final String DEFAULT_DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * 获取当前时间的字符串表示 (yyyy-MM-dd HH:mm:ss)
     */
    public static String getCurrentDateTimeStr() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(DEFAULT_DATETIME_PATTERN));
    }

    /**
     * 将 LocalDateTime 格式化为字符串
     */
    public static String formatDateTime(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.format(DateTimeFormatter.ofPattern(DEFAULT_DATETIME_PATTERN));
    }

    /**
     * 将字符串解析为 LocalDateTime
     */
    public static LocalDateTime parseDateTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(timeStr, DateTimeFormatter.ofPattern(DEFAULT_DATETIME_PATTERN));
    }

    /**
     * 生成不带中划线的 UUID
     */
    public static String getUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
