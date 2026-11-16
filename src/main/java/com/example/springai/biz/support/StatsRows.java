package com.example.springai.biz.support;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

/**
 * {@code BaseMapper#selectMaps} 聚合结果的行取值助手。
 *
 * <p>MyBatis 对 Map 结果类型不做驼峰映射，键即 JDBC 返回的列标签（PostgreSQL 默认小写），
 * 一旦别名大小写或下划线风格与预期不一致就会静默取空。这里统一做「忽略大小写与下划线」
 * 的匹配，把这类隐性故障挡在聚合逻辑之外。
 */
public final class StatsRows {

    private StatsRows() {
    }

    /** 按别名取值；{@code group_key} / {@code groupKey} / {@code GROUP_KEY} 均可命中。 */
    public static Object pick(Map<String, Object> row, String alias) {
        if (row == null || alias == null) {
            return null;
        }
        String target = canonical(alias);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && canonical(entry.getKey()).equals(target)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** 转字符串；null 或空白转 "-"。 */
    public static String text(Object value) {
        if (value == null) {
            return "-";
        }
        String text = String.valueOf(value);
        return text.isBlank() ? "-" : text;
    }

    /** 转 Long；无法解析时返回 null。 */
    public static Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 转 BigDecimal；null 或空白返回 0，无法解析时不抛异常而返回 0。 */
    public static BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static String canonical(String name) {
        return name.replace("_", "").toLowerCase(Locale.ROOT);
    }
}
