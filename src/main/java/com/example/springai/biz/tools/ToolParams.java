package com.example.springai.biz.tools;

import com.example.springai.biz.support.BizLabels;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.UnaryOperator;

/**
 * AI 工具入参归一化助手（包内可见，由订阅域与订单域工具类共用）。
 *
 * <p>大模型给出的参数往往「看起来合理但不精确」：日期常只给到天，分页可能给 0
 * 或极大的值，状态可能给中文。而 Service 层的查询实现要求严格格式（例如
 * {@code pageVO} 的时间参数必须是 {@code yyyy-MM-dd HH:mm:ss}，格式不符直接抛
 * {@code DateTimeParseException}）。把这些容错统一收敛在工具层，可以让 Service
 * 保持既有语义、避免影响管理后台的调用方。
 *
 * <p>校验失败统一抛 {@link IllegalArgumentException}，由工具方法捕获后转成
 * 带 {@code error} 的结果返回给模型，模型可据此自我纠正。
 */
final class ToolParams {

    /** 默认页码。 */
    static final int DEFAULT_PAGE = 1;

    /** 默认每页条数：刻意取小值，避免工具结果把模型上下文撑爆。 */
    static final int DEFAULT_SIZE = 10;

    /** 每页条数上限。 */
    static final int MAX_SIZE = 20;

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private ToolParams() {
    }

    /** 页码保护：null 或小于 1 一律归到第 1 页。 */
    static int page(Integer page) {
        return (page == null || page < 1) ? DEFAULT_PAGE : page;
    }

    /** 每页条数保护：null 或小于 1 归到默认值，超过上限直接截断。 */
    static int size(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /**
     * 归一化时间区间边界为 Service 要求的 {@code yyyy-MM-dd HH:mm:ss}。
     *
     * @param value 模型给出的日期，支持 {@code yyyy-MM-dd} 与 {@code yyyy-MM-dd HH:mm:ss}
     * @param start {@code true}=区间起点（仅日期时补 00:00:00）；
     *              {@code false}=区间终点（仅日期时补 23:59:59，避免漏掉当天数据）
     * @return 归一化后的字符串；入参为空时返回 {@code null}，表示不限制该边界
     * @throws IllegalArgumentException 格式无法识别时抛出
     */
    static String normalizeBound(String value, boolean start) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();

        try {
            return LocalDateTime.parse(trimmed, DATE_TIME).format(DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // 继续尝试「只给到天」的常见写法
        }

        try {
            LocalDate date = LocalDate.parse(trimmed, DATE);
            LocalTime time = start ? LocalTime.MIN : LocalTime.MAX.withNano(0);
            return date.atTime(time).format(DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "日期格式不正确：" + trimmed + "，应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
    }

    /** 归一化订阅状态：支持英文码与中文标签；入参为空返回 null，非法值抛异常。 */
    static String subscriptionStatus(String value) {
        return normalizeEnum(value, "订阅状态",
                BizLabels.SUBSCRIPTION_STATUS_CODES, BizLabels::normalizeSubscriptionStatus);
    }

    /** 归一化订单状态：支持英文码与中文标签；入参为空返回 null，非法值抛异常。 */
    static String orderStatus(String value) {
        return normalizeEnum(value, "订单状态",
                BizLabels.ORDER_STATUS_CODES, BizLabels::normalizeOrderStatus);
    }

    /** 归一化订单类型：支持英文码与中文标签；入参为空返回 null，非法值抛异常。 */
    static String orderType(String value) {
        return normalizeEnum(value, "订单类型",
                BizLabels.ORDER_TYPE_CODES, BizLabels::normalizeOrderType);
    }

    private static String normalizeEnum(String value, String fieldName,
                                        String allowedCodes,
                                        UnaryOperator<String> normalizer) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = normalizer.apply(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + "取值非法：" + value.trim()
                    + "，可选值：" + allowedCodes);
        }
        return normalized;
    }
}
