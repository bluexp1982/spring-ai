package com.example.springai.biz.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 统计聚合结果项。
 *
 * <p>统一承载「分组 + 指标」二元信息，使订阅域与订单域、计数与金额等不同统计
 * 场景共用同一个结构。工具层可直接把它映射为 Mermaid 饼图/柱状图的数据点。
 */
@Data
public class StatsItemVO {

    /** 分组原始值：如 active / 3 / 2026-07。 */
    private String key;

    /** 分组中文展示名：如 生效中 / 专业版 / 2026-07。 */
    private String label;

    /** 指标值：笔数或金额。统一用 BigDecimal 承载，避免聚合结果精度丢失。 */
    private BigDecimal value;

    public static StatsItemVO of(String key, String label, BigDecimal value) {
        StatsItemVO item = new StatsItemVO();
        item.key = key;
        item.label = label;
        item.value = value == null ? BigDecimal.ZERO : value;
        return item;
    }
}
