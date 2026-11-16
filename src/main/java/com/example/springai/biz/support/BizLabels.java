package com.example.springai.biz.support;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务枚举中英文映射的单一来源。
 *
 * <p>数据库存的是英文码（如 {@code active}），而面向用户与模型的输出需要中文；
 * 同时大模型给出的参数可能是中文（如「生效中」）也可能是英文，需要在入参侧归一。
 * 把这两件事收敛到同一个类，避免映射散落在工具类与统计实现中。
 *
 * <p>可选值清单（{@code *_CODES}）刻意写成字面量：{@code @Tool} 描述的注解值必须是
 * 编译期常量，无法引用 {@code String.join(...)} 的结果。映射表由清单派生，
 * 两者数量不一致会在类初始化时立刻失败，因此不会出现清单与映射漂移。
 */
public final class BizLabels {

    /** 订阅状态可选值，顺序即展示顺序。 */
    public static final String SUBSCRIPTION_STATUS_CODES = "active/in_trial/paused/expired/cancelled";

    /** 订单状态可选值，顺序即展示顺序。 */
    public static final String ORDER_STATUS_CODES = "pending/paid/failed/refunded/expired";

    /** 订单类型可选值，顺序即展示顺序。 */
    public static final String ORDER_TYPE_CODES = "new/renewal/upgrade/downgrade/refund/cancel";

    /** 订阅状态：英文码 → 中文标签。 */
    private static final Map<String, String> SUBSCRIPTION_STATUS = labelsOf(
            SUBSCRIPTION_STATUS_CODES, "生效中", "试用中", "已暂停", "已过期", "已取消");

    /** 订单状态：英文码 → 中文标签。 */
    private static final Map<String, String> ORDER_STATUS = labelsOf(
            ORDER_STATUS_CODES, "待支付", "已支付", "支付失败", "已退款", "已失效");

    /** 订单类型：英文码 → 中文标签。 */
    private static final Map<String, String> ORDER_TYPE = labelsOf(
            ORDER_TYPE_CODES, "新购", "续费", "升级", "降级", "退款", "取消");

    private BizLabels() {
    }

    /** 归一化订阅状态：英文码或中文标签均可，无法识别返回 null。 */
    public static String normalizeSubscriptionStatus(String value) {
        return normalize(value, SUBSCRIPTION_STATUS);
    }

    /** 归一化订单状态：英文码或中文标签均可，无法识别返回 null。 */
    public static String normalizeOrderStatus(String value) {
        return normalize(value, ORDER_STATUS);
    }

    /** 归一化订单类型：英文码或中文标签均可，无法识别返回 null。 */
    public static String normalizeOrderType(String value) {
        return normalize(value, ORDER_TYPE);
    }

    /** 订阅状态码 → 中文标签；未知码原样返回，null → "-"。 */
    public static String subscriptionStatusLabel(String code) {
        return label(code, SUBSCRIPTION_STATUS);
    }

    /** 订单状态码 → 中文标签；未知码原样返回，null → "-"。 */
    public static String orderStatusLabel(String code) {
        return label(code, ORDER_STATUS);
    }

    /** 订单类型码 → 中文标签；未知码原样返回，null → "-"。 */
    public static String orderTypeLabel(String code) {
        return label(code, ORDER_TYPE);
    }

    /** 由「斜杠分隔的码清单 + 等长中文标签」构造有序映射，数量不一致立即暴露。 */
    private static Map<String, String> labelsOf(String codes, String... labels) {
        String[] keys = codes.split("/");
        if (keys.length != labels.length) {
            throw new IllegalStateException(
                    "业务枚举码与标签数量不一致：codes=" + codes + ", labels=" + labels.length);
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keys.length; i++) {
            map.put(keys[i], labels[i]);
        }
        return map;
    }

    private static String normalize(String value, Map<String, String> dict) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : dict.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(trimmed) || entry.getValue().equals(trimmed)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static String label(String code, Map<String, String> dict) {
        if (code == null) {
            return "-";
        }
        String normalized = normalize(code, dict);
        // 未知取值原样返回，便于模型与开发者定位脏数据，而不是统一显示成 "-"
        return normalized == null ? code : dict.get(normalized);
    }
}
