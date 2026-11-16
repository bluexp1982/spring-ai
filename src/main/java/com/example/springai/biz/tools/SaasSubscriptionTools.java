package com.example.springai.biz.tools;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.springai.biz.entity.SaasSubscription;
import com.example.springai.biz.service.SaasSubscriptionService;
import com.example.springai.biz.support.BizLabels;
import com.example.springai.biz.vo.SaasSubscriptionVO;
import com.example.springai.biz.vo.StatsItemVO;
import com.example.springai.common.util.TimeFormats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 订阅域 AI 工具集：供大模型在对话中按需调用。
 *
 * <p>只开放<b>只读查询</b>，写操作（create / updateById / deleteById）不暴露给模型，
 * 避免模型误触发数据变更。提供三类能力：
 * <ul>
 *   <li>单条详情：{@link #getSubscriptionById(Long)}</li>
 *   <li>多条件分页列表：{@link #querySubscriptions(Long, Long, String, Integer, Integer)}</li>
 *   <li>统计聚合（供图表）：{@link #statsSubscriptions(String, Long, Long, String)}</li>
 * </ul>
 *
 * <p>注意：{@code @Tool} 只是标注，必须由 {@code AiChatService} 通过
 * {@code ChatClient#defaultTools(...)} 注册本 Bean 后才会真正生效。
 *
 * <p>本类不参与任何 AOP 增强，因此不会被代理，Spring AI 反射调用时拿到的就是
 * 真实实例，不存在代理对象方法反射失败的隐患。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SaasSubscriptionTools {

    private final SaasSubscriptionService saasSubscriptionService;

    /**
     * 查询单个订阅的详情。
     *
     * <p>返回值刻意不直接用实体：只挑关键字段组成 Map。一是避免整行订阅
     * （含 metadata、OffsetDateTime 等）序列化后 token 开销过大，二是工具结果
     * 统一会被 JSON 序列化，Map 出来的结构比 JSON 字符串更干净、模型更好读。
     *
     * @param id 订阅 ID
     * @return 订阅关键信息；参数缺失或查不到时返回带 {@code error} 的说明
     */
    @Tool(name = "getSubscriptionById",
            description = "根据订阅ID查询订阅详情，返回用户ID、套餐ID、订阅状态、是否自动续费、"
                    + "当前计费周期以及开始/到期/取消/暂停时间等关键信息")
    public Map<String, Object> getSubscriptionById(@ToolParam(description = "订阅ID") Long id) {
        if (id == null) {
            return Map.of("error", "参数缺失：请提供订阅ID");
        }
        SaasSubscription subscription = saasSubscriptionService.getById(id);
        if (subscription == null) {
            return Map.of("error", "未找到 ID 为 " + id + " 的订阅");
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("subscriptionId", subscription.getId());
        detail.put("userId", subscription.getUserId());
        detail.put("planId", subscription.getPlanId());
        detail.put("status", orDash(subscription.getStatus()));
        detail.put("autoRenew", subscription.getAutoRenew());
        detail.put("startedAt", TimeFormats.display(subscription.getStartedAt()));
        detail.put("expiredAt", TimeFormats.display(subscription.getExpiredAt()));
        detail.put("trialEndAt", TimeFormats.display(subscription.getTrialEndAt()));
        detail.put("cancelledAt", TimeFormats.display(subscription.getCancelledAt()));
        detail.put("pausedAt", TimeFormats.display(subscription.getPausedAt()));
        detail.put("pausedUntil", TimeFormats.display(subscription.getPausedUntil()));
        detail.put("currentPeriodStart", TimeFormats.display(subscription.getCurrentPeriodStart()));
        detail.put("currentPeriodEnd", TimeFormats.display(subscription.getCurrentPeriodEnd()));
        detail.put("createdAt", TimeFormats.display(subscription.getCreatedAt()));
        return detail;
    }

    /**
     * 多条件分页查询订阅列表。
     *
     * <p>所有筛选条件均可选，省略即不限制。返回 {@code userName}/{@code planName}
     * 由 Service 批量补全，模型无需再去查用户与套餐表。
     *
     * @return 分页结果：{@code total/page/size/pages/records}；参数非法或查询异常时返回 {@code error}
     */
    @Tool(name = "querySubscriptions",
            description = "按条件分页查询订阅列表，返回订阅ID、用户ID、用户名、套餐ID、套餐名、订阅状态、"
                    + "是否自动续费以及开始/到期/当前计费周期结束时间。所有筛选条件均为可选，省略即不限制。"
                    + "订阅状态可选值：" + BizLabels.SUBSCRIPTION_STATUS_CODES
                    + "（也接受中文，如“生效中”）。"
                    + "每页最多 " + ToolParams.MAX_SIZE + " 条、默认 " + ToolParams.DEFAULT_SIZE
                    + " 条，返回 pages 字段表示总页数，需要更多数据请增大 page 或缩小筛选范围")
    public Map<String, Object> querySubscriptions(
            @ToolParam(description = "用户ID，可选", required = false) Long userId,
            @ToolParam(description = "套餐ID，可选", required = false) Long planId,
            @ToolParam(description = "订阅状态，可选。可选值：" + BizLabels.SUBSCRIPTION_STATUS_CODES,
                    required = false) String status,
            @ToolParam(description = "页码，从1开始，默认1", required = false) Integer page,
            @ToolParam(description = "每页条数，默认" + ToolParams.DEFAULT_SIZE
                    + "，最大" + ToolParams.MAX_SIZE, required = false) Integer size) {
        try {
            SaasSubscription query = new SaasSubscription();
            query.setUserId(userId);
            query.setPlanId(planId);
            query.setStatus(ToolParams.subscriptionStatus(status));

            // 模型侧只暴露 userId / planId 精确过滤，用户名模糊匹配参数（userName）传 null
            IPage<SaasSubscriptionVO> result = saasSubscriptionService.pageVO(
                    ToolParams.page(page), ToolParams.size(size), query, null);
            return pageResult(result, toRecords(result.getRecords()));
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        } catch (Exception e) {
            log.error("querySubscriptions 执行失败, userId={}, planId={}, status={}",
                    userId, planId, status, e);
            return Map.of("error", "订阅查询失败：" + e.getMessage());
        }
    }

    /**
     * 订阅统计聚合：按维度汇总订阅数量。
     *
     * <p>输出结构刻意做成「分组键 + 中文名 + 数值」，模型可直接映射为 Mermaid
     * 饼图的数据点，无需自己再算一遍。
     */
    @Tool(name = "statsSubscriptions",
            description = "订阅统计：按维度汇总订阅数量，用于生成占比/分布类图表。"
                    + "dimension 可选值：status（按订阅状态，默认）、plan（按套餐）。"
                    + "返回 items 列表，每项含 key（分组原始值）、label（中文名）、value（数量），"
                    + "已按数量倒序，可直接作为 Mermaid 饼图的数据源；total 为合计数量")
    public Map<String, Object> statsSubscriptions(
            @ToolParam(description = "统计维度：status 或 plan，默认 status", required = false) String dimension,
            @ToolParam(description = "用户ID，可选，省略表示统计全部用户", required = false) Long userId,
            @ToolParam(description = "套餐ID，可选", required = false) Long planId,
            @ToolParam(description = "订阅状态，可选。可选值：" + BizLabels.SUBSCRIPTION_STATUS_CODES,
                    required = false) String status) {
        try {
            String dim = (dimension == null || dimension.isBlank()) ? "status" : dimension.trim();

            SaasSubscription query = new SaasSubscription();
            query.setUserId(userId);
            query.setPlanId(planId);
            query.setStatus(ToolParams.subscriptionStatus(status));

            return statsResult(dim, "count", saasSubscriptionService.stats(dim, query));
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        } catch (Exception e) {
            log.error("statsSubscriptions 执行失败, dimension={}", dimension, e);
            return Map.of("error", "订阅统计失败：" + e.getMessage());
        }
    }

    /** 把订阅分页结果裁剪为模型友好的精简结构。 */
    private static List<Map<String, Object>> toRecords(List<SaasSubscriptionVO> records) {
        List<Map<String, Object>> items = new ArrayList<>(records.size());
        for (SaasSubscriptionVO vo : records) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("subscriptionId", vo.getId());
            item.put("userId", vo.getUserId());
            item.put("userName", orDash(vo.getUserName()));
            item.put("planId", vo.getPlanId());
            item.put("planName", orDash(vo.getPlanName()));
            item.put("status", BizLabels.subscriptionStatusLabel(vo.getStatus()));
            item.put("autoRenew", vo.getAutoRenew());
            item.put("startedAt", TimeFormats.display(vo.getStartedAt()));
            item.put("expiredAt", TimeFormats.display(vo.getExpiredAt()));
            item.put("currentPeriodEnd", TimeFormats.display(vo.getCurrentPeriodEnd()));
            item.put("createdAt", TimeFormats.display(vo.getCreatedAt()));
            items.add(item);
        }
        return items;
    }

    /** 构造分页结果信封：显式给出 total 与 pages，模型才能判断是否需要翻页。 */
    private static Map<String, Object> pageResult(IPage<?> page, List<Map<String, Object>> records) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", page.getTotal());
        result.put("page", page.getCurrent());
        result.put("size", page.getSize());
        result.put("pages", page.getPages());
        result.put("records", records);
        return result;
    }

    /** 构造统计结果信封：total 为各分组指标值之和，便于模型直接引用合计。 */
    private static Map<String, Object> statsResult(String dimension, String metric,
                                                   List<StatsItemVO> items) {
        BigDecimal total = items.stream()
                .map(StatsItemVO::getValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dimension", dimension);
        result.put("metric", metric);
        result.put("itemCount", items.size());
        result.put("total", total);
        result.put("items", items);
        return result;
    }

    /** 把 null 统一转成 "-"，避免模型读到无意义的 null。 */
    private static String orDash(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }
}
