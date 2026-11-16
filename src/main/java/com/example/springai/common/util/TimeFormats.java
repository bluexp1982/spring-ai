package com.example.springai.common.util;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * AI 工具结果的时间统一格式化。
 *
 * <p>数据库 TIMESTAMPTZ 经 JDBC 读出的 OffsetDateTime 偏移量通常是 UTC，
 * 直接 toString() 会得到带 Z 的 ISO-8601 字符串，模型会据此标注 (UTC)，
 * 造成展示歧义。这里统一换算到展示时区再格式化。
 */
public final class TimeFormats {

    /** 展示时区：北京时间（UTC+8）。 */
    public static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TimeFormats() {
    }

    /** null → "-"；OffsetDateTime → 展示时区的 "yyyy-MM-dd HH:mm:ss (北京时间)"；其余原样返回。 */
    public static String display(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof OffsetDateTime odt) {
            return odt.atZoneSameInstant(DISPLAY_ZONE).format(DATE_TIME) + " (北京时间)";
        }
        return String.valueOf(value);
    }
}
