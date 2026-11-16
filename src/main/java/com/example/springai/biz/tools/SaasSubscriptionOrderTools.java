package com.example.springai.biz.tools;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.springai.biz.entity.SaasSubscriptionOrder;
import com.example.springai.biz.service.SaasSubscriptionOrderService;
import com.example.springai.biz.support.BizLabels;
import com.example.springai.biz.vo.SaasSubscriptionOrderVO;
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
 * 订单域 AI 工具集：供大模型在对话中按需调用。
 *
 * <p>只开放<b>只读查询</b>，写操作（create / updateById / deleteById）不暴露给模型，
 * 避免模型误触发数据变更。提供三类能力：
 * <ul>
 *   <li>单条详情：{@link #getOrderById(Long)}</li>
 *   <li>多条件分页列表：{@link #queryOrders}</li>
 *   <li>统计聚合（供图表）：{@link #statsOrders}</li>
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
public class SaasSubscriptionOrderTools {

    private final SaasSubscriptionOrderService saasSubscriptionOrderService;

    /**
     * 查询单个订单的详情。
     *
     * <p>返回值刻意不直接用实体：只挑关键字段组成 Map。一是避免整行订单
     * （含 metadata、OffsetDateTime 等）序列化后 token 开销过大，二是工具结果
     * 统一会被 JSON 序列化，Map 出来的结构比 JSON 字符串更干净、模型更好读。
     *
     * @param id 订单 ID
     * @return 订单关键信息；参数缺失或查不到时返回带 {@code error} 的说明
     */
    @Tool(name = "getOrderById",
            description = "根据订单ID查询订单详情，返回订单号、状态、金额、支付方式与时间等关键信息")
    public Map<String, Object> getOrderById(@ToolParam(description = "订单ID") Long id) {
        if (id == null) {
            return Map.of("error", "参数缺失：请提供订单ID");
        }
        SaasSubscriptionOrder order = saasSubscriptionOrderService.getById(id);
        if (order == null) {
            return Map.of("error", "未找到 ID 为 " + id + " 的订单");
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("orderId", order.getId());
        detail.put("orderNo", orDash(order.getOrderNo()));
        detail.put("status", orDash(order.getStatus()));
        detail.put("orderType", orDash(order.getOrderType()));
        detail.put("currency", orDash(order.getCurrency()));
        detail.put("amount", order.getAmount());
        detail.put("discountAmount", order.getDiscountAmount());
        detail.put("finalAmount", order.getFinalAmount());
        detail.put("paymentMethod", orDash(order.getPaymentMethod()));
        detail.put("paymentChannel", orDash(order.getPaymentChannel()));
        detail.put("paidAt", TimeFormats.display(order.getPaidAt()));
        detail.put("periodStart", TimeFormats.display(order.getPeriodStart()));
        detail.put("periodEnd", TimeFormats.display(order.getPeriodEnd()));
        detail.put("createdAt", TimeFormats.display(order.getCreatedAt()));
        return detail;
    }

    /**
     * 多条件分页查询订单列表。
     *
     * <p>日期容错在 {@link ToolParams#normalizeBound(String, boolean)} 中完成：
     * Service 的时间参数是严格解析，模型只给到天时会抛异常，因此在这里补齐时刻。
     *
     * @return 分页结果：{@code total/page/size/pages/records}；参数非法或查询异常时返回 {@code error}
     */
    @Tool(name = "queryOrders",
            description = "按条件分页查询订单列表，返回订单ID、订单号、用户ID、用户名、套餐ID、套餐名、"
                    + "订单类型、订单状态、金额、实付金额、支付方式与下单/支付时间。"
                    + "所有筛选条件均为可选，省略即不限制；订单号支持模糊匹配。"
                    + "订单状态可选值：" + BizLabels.ORDER_STATUS_CODES
                    + "；订单类型可选值：" + BizLabels.ORDER_TYPE_CODES
                    + "（状态与类型也接受中文，如“已支付”“续费”）。"
                    + "startDate/endDate 用于按创建时间过滤，支持 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss，"
                    + "只给日期时起点按当天 00:00:00、终点按当天 23:59:59 处理。"
                    + "每页最多 " + ToolParams.MAX_SIZE + " 条、默认 " + ToolParams.DEFAULT_SIZE
                    + " 条，返回 pages 字段表示总页数")
    public Map<String, Object> queryOrders(
            @ToolParam(description = "订单号，支持模糊匹配，可选", required = false) String orderNo,
            @ToolParam(description = "用户ID，可选", required = false) Long userId,
            @ToolParam(description = "套餐ID，可选", required = false) Long planId,
            @ToolParam(description = "订单状态，可选。可选值：" + BizLabels.ORDER_STATUS_CODES,
                    required = false) String status,
            @ToolParam(description = "订单类型，可选。可选值：" + BizLabels.ORDER_TYPE_CODES,
                    required = false) String orderType,
            @ToolParam(description = "创建时间下界，格式 yyyy-MM-dd，可选", required = false) String startDate,
            @ToolParam(description = "创建时间上界，格式 yyyy-MM-dd，可选", required = false) String endDate,
            @ToolParam(description = "页码，从1开始，默认1", required = false) Integer page,
            @ToolParam(description = "每页条数，默认" + ToolParams.DEFAULT_SIZE
                    + "，最大" + ToolParams.MAX_SIZE, required = false) Integer size) {
        try {
            SaasSubscriptionOrder query = new SaasSubscriptionOrder();
            query.setOrderNo(trimToNull(orderNo));
            query.setUserId(userId);
            query.setPlanId(planId);
            query.setStatus(ToolParams.orderStatus(status));
            query.setOrderType(ToolParams.orderType(orderType));

            // 模型侧只暴露 userId / planId 精确过滤，用户名模糊匹配参数（userName）传 null
            IPage<SaasSubscriptionOrderVO> result = saasSubscriptionOrderService.pageVO(
                    ToolParams.page(page), ToolParams.size(size), query,
                    null,
                    ToolParams.normalizeBound(startDate, true),
                    ToolParams.normalizeBound(endDate, false));
            return pageResult(result, toRecords(result.getRecords()));
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        } catch (Exception e) {
            log.error("queryOrders 执行失败, orderNo={}, userId={}, status={}, orderType={}, startDate={}, endDate={}",
                    orderNo, userId, status, orderType, startDate, endDate, e);
            return Map.of("error", "订单查询失败：" + e.getMessage());
        }
    }

    /**
     * 订单统计聚合：按维度汇总笔数或金额。
     *
     * <p>输出结构刻意做成「分组键 + 中文名 + 数值」，模型可直接映射为 Mermaid
     * 饼图/柱状图的数据点，无需自己再算一遍。
     */
    @Tool(name = "statsOrders",
            description = "订单统计：按维度汇总订单笔数或金额，用于生成占比/趋势类图表。"
                    + "dimension 可选值：status（按订单状态，默认）、orderType（按订单类型）、"
                    + "plan（按套餐）、month（按创建月份，北京时间）。"
                    + "metric 可选值：count（笔数，默认）、amount（实付金额之和，单位元）。"
                    + "month 维度按时间正序返回（便于画趋势），其余维度按指标值倒序。"
                    + "返回 items 列表，每项含 key（分组原始值）、label（中文名）、value（指标值），"
                    + "可直接作为 Mermaid 饼图/柱状图的数据源；total 为合计值。"
                    + "也可用 status/orderType/userId/planId/startDate/endDate 限定统计范围")
    public Map<String, Object> statsOrders(
            @ToolParam(description = "统计维度：status / orderType / plan / month，默认 status",
                    required = false) String dimension,
            @ToolParam(description = "统计指标：count（笔数）/ amount（实付金额），默认 count",
                    required = false) String metric,
            @ToolParam(description = "订单状态，可选。可选值：" + BizLabels.ORDER_STATUS_CODES,
                    required = false) String status,
            @ToolParam(description = "订单类型，可选。可选值：" + BizLabels.ORDER_TYPE_CODES,
                    required = false) String orderType,
            @ToolParam(description = "用户ID，可选", required = false) Long userId,
            @ToolParam(description = "套餐ID，可选", required = false) Long planId,
            @ToolParam(description = "创建时间下界，格式 yyyy-MM-dd，可选", required = false) String startDate,
            @ToolParam(description = "创建时间上界，格式 yyyy-MM-dd，可选", required = false) String endDate) {
        try {
            String dim = (dimension == null || dimension.isBlank()) ? "status" : dimension.trim();
            String met = (metric == null || metric.isBlank()) ? "count" : metric.trim();

            SaasSubscriptionOrder query = new SaasSubscriptionOrder();
            query.setStatus(ToolParams.orderStatus(status));
            query.setOrderType(ToolParams.orderType(orderType));
            query.setUserId(userId);
            query.setPlanId(planId);

            List<StatsItemVO> items = saasSubscriptionOrderService.stats(dim, met, query,
                    ToolParams.normalizeBound(startDate, true),
                    ToolParams.normalizeBound(endDate, false));
            return statsResult(dim, met, items);
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        } catch (Exception e) {
            log.error("statsOrders 执行失败, dimension={}, metric={}", dimension, metric, e);
            return Map.of("error", "订单统计失败：" + e.getMessage());
        }
    }

    /** 把订单分页结果裁剪为模型友好的精简结构。 */
    private static List<Map<String, Object>> toRecords(List<SaasSubscriptionOrderVO> records) {
        List<Map<String, Object>> items = new ArrayList<>(records.size());
        for (SaasSubscriptionOrderVO vo : records) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orderId", vo.getId());
            item.put("orderNo", orDash(vo.getOrderNo()));
            item.put("userId", vo.getUserId());
            item.put("userName", orDash(vo.getUserName()));
            item.put("planId", vo.getPlanId());
            item.put("planName", orDash(vo.getPlanName()));
            item.put("orderType", BizLabels.orderTypeLabel(vo.getOrderType()));
            item.put("status", BizLabels.orderStatusLabel(vo.getStatus()));
            item.put("currency", orDash(vo.getCurrency()));
            item.put("amount", vo.getAmount());
            item.put("discountAmount", vo.getDiscountAmount());
            item.put("finalAmount", vo.getFinalAmount());
            item.put("paymentMethod", orDash(vo.getPaymentMethod()));
            item.put("paidAt", TimeFormats.display(vo.getPaidAt()));
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

    /** 空白字符串归一为 null，避免把空串当模糊匹配条件拼进 SQL。 */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 把 null 统一转成 "-"，避免模型读到无意义的 null。 */
    private static String orDash(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }
}
