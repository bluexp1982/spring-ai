package com.example.springai.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.springai.biz.entity.SaasPlan;
import com.example.springai.biz.entity.SaasSubscriptionOrder;
import com.example.springai.biz.entity.SysUser;
import com.example.springai.biz.mapper.SaasPlanMapper;
import com.example.springai.biz.mapper.SaasSubscriptionOrderMapper;
import com.example.springai.biz.mapper.SysUserMapper;
import com.example.springai.biz.service.SaasSubscriptionOrderService;
import com.example.springai.biz.support.BizLabels;
import com.example.springai.biz.support.StatsRows;
import com.example.springai.biz.vo.SaasSubscriptionOrderVO;
import com.example.springai.biz.vo.StatsItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaasSubscriptionOrderServiceImpl implements SaasSubscriptionOrderService {

    /** 聚合结果中「分组键」列的别名，需与 select 语句保持一致。 */
    private static final String GROUP_KEY_ALIAS = "group_key";

    /** 聚合结果中「指标值」列的别名。 */
    private static final String METRIC_ALIAS = "metric_value";

    /** 时间边界解析格式：与 {@link #buildWrapper} 既有约定一致。 */
    private static final DateTimeFormatter BOUND_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 月份维度分组表达式：按展示时区（北京时间）取年月，与 TimeFormats 保持一致。 */
    private static final String MONTH_GROUP_EXPR =
            "to_char(created_at AT TIME ZONE 'Asia/Shanghai', 'YYYY-MM')";

    private final SaasSubscriptionOrderMapper saasSubscriptionOrderMapper;
    private final SysUserMapper sysUserMapper;
    private final SaasPlanMapper saasPlanMapper;

    @Override
    public SaasSubscriptionOrder getById(Long id) {
        return saasSubscriptionOrderMapper.selectById(id);
    }

    @Override
    @Transactional
    public boolean create(SaasSubscriptionOrder entity) {
        return saasSubscriptionOrderMapper.insert(entity) > 0;
    }

    @Override
    @Transactional
    public boolean updateById(SaasSubscriptionOrder entity) {
        return saasSubscriptionOrderMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional
    public boolean deleteById(Long id) {
        return saasSubscriptionOrderMapper.deleteById(id) > 0;
    }

    @Override
    public IPage<SaasSubscriptionOrder> page(int page, int size, SaasSubscriptionOrder query) {
        return saasSubscriptionOrderMapper.selectPage(new Page<>(page, size),
                buildWrapper(query, null, null, null));
    }

    @Override
    public IPage<SaasSubscriptionOrderVO> pageVO(int page, int size, SaasSubscriptionOrder query,
                                                 String userName,
                                                 String startDate, String endDate) {
        // 1. 分页查询实体
        IPage<SaasSubscriptionOrder> entityPage = saasSubscriptionOrderMapper.selectPage(
                new Page<>(page, size), buildWrapper(query, userName, startDate, endDate));

        // 2. 收集所有 userId 和 planId
        List<SaasSubscriptionOrder> records = entityPage.getRecords();
        if (records.isEmpty()) {
            return new Page<SaasSubscriptionOrderVO>().setTotal(entityPage.getTotal())
                    .setCurrent(entityPage.getCurrent()).setSize(entityPage.getSize());
        }

        Set<Long> userIds = records.stream()
                .map(SaasSubscriptionOrder::getUserId).collect(Collectors.toSet());
        Set<Long> planIds = records.stream()
                .map(SaasSubscriptionOrder::getPlanId).collect(Collectors.toSet());

        // 3. 批量查询用户和套餐
        Map<Long, String> userNameMap = userIds.isEmpty() ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId,
                                u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername(),
                                (a, b) -> a));
        Map<Long, String> planNameMap = planIds.isEmpty() ? Collections.emptyMap()
                : saasPlanMapper.selectBatchIds(planIds).stream()
                        .collect(Collectors.toMap(SaasPlan::getId, SaasPlan::getName, (a, b) -> a));

        // 4. 组装 VO
        List<SaasSubscriptionOrderVO> voList = records.stream().map(entity -> {
            SaasSubscriptionOrderVO vo = new SaasSubscriptionOrderVO();
            copyFields(entity, vo);
            vo.setUserName(userNameMap.getOrDefault(entity.getUserId(), null));
            vo.setPlanName(planNameMap.getOrDefault(entity.getPlanId(), null));
            return vo;
        }).collect(Collectors.toList());

        Page<SaasSubscriptionOrderVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize());
        voPage.setTotal(entityPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public List<StatsItemVO> stats(String dimension, String metric,
                                   SaasSubscriptionOrder query,
                                   String startDate, String endDate) {
        String dim = canonical(dimension);
        String met = canonical(metric);
        String groupExpression = groupExpression(dim);
        String metricExpression = metricExpression(met);

        QueryWrapper<SaasSubscriptionOrder> wrapper = new QueryWrapper<>();
        wrapper.select(groupExpression + " AS " + GROUP_KEY_ALIAS,
                metricExpression + " AS " + METRIC_ALIAS);
        applyStatsFilters(wrapper, query, startDate, endDate);
        wrapper.groupBy(groupExpression);
        // 月份维度按时间正序（趋势展示需要），其余维度按指标值倒序（突出头部）
        if ("month".equals(dim)) {
            wrapper.orderByAsc(GROUP_KEY_ALIAS);
        } else {
            wrapper.orderByDesc(METRIC_ALIAS);
        }

        List<Map<String, Object>> rows = saasSubscriptionOrderMapper.selectMaps(wrapper);
        Map<Long, String> planNameMap = "plan".equals(dim) ? loadPlanNames(rows) : Collections.emptyMap();

        return rows.stream().map(row -> {
            String key = StatsRows.text(StatsRows.pick(row, GROUP_KEY_ALIAS));
            return StatsItemVO.of(key, toLabel(dim, key, planNameMap),
                    StatsRows.decimal(StatsRows.pick(row, METRIC_ALIAS)));
        }).toList();
    }

    /** 维度/指标归一化：忽略大小写、下划线与连字符，便于模型自由书写（如 order_type / orderType）。 */
    private static String canonical(String value) {
        return value == null ? ""
                : value.replace("_", "").replace("-", "").trim().toLowerCase(Locale.ROOT);
    }

    /** 维度 → 分组 SQL 表达式。 */
    private static String groupExpression(String dimension) {
        return switch (dimension) {
            case "status" -> "status";
            case "ordertype" -> "order_type";
            case "plan" -> "plan_id";
            case "month" -> MONTH_GROUP_EXPR;
            default -> throw new IllegalArgumentException(
                    "不支持的订单统计维度：" + dimension + "，可选值：status、orderType、plan、month");
        };
    }

    /** 指标 → 聚合 SQL 表达式。 */
    private static String metricExpression(String metric) {
        return switch (metric) {
            case "count" -> "COUNT(*)";
            case "amount" -> "COALESCE(SUM(final_amount), 0)";
            default -> throw new IllegalArgumentException(
                    "不支持的统计指标：" + metric + "，可选值：count、amount");
        };
    }

    /** 分组原始值 → 中文展示名。 */
    private static String toLabel(String dimension, String key, Map<Long, String> planNameMap) {
        return switch (dimension) {
            case "status" -> BizLabels.orderStatusLabel(key);
            case "ordertype" -> BizLabels.orderTypeLabel(key);
            case "plan" -> {
                Long planId = StatsRows.longValue(key);
                yield planId == null ? key : planNameMap.getOrDefault(planId, "套餐#" + planId);
            }
            // month 的 key 本身就是可读的年月，无需再映射
            default -> key;
        };
    }

    /** 按 plan_id 聚合后批量换取套餐名，避免 JOIN 与 N+1。 */
    private Map<Long, String> loadPlanNames(List<Map<String, Object>> rows) {
        Set<Long> planIds = rows.stream()
                .map(row -> StatsRows.longValue(StatsRows.pick(row, GROUP_KEY_ALIAS)))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (planIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return saasPlanMapper.selectByIds(planIds).stream()
                .collect(Collectors.toMap(SaasPlan::getId, SaasPlan::getName, (a, b) -> a));
    }

    /**
     * 统计用的过滤条件：覆盖 {@link #buildWrapper} 的实体字段过滤（不含用户名模糊匹配，
     * 统计不接受名称条件），差别仅在于使用字符串列名（{@code QueryWrapper} 不支持 lambda 列引用）。
     */
    private void applyStatsFilters(QueryWrapper<SaasSubscriptionOrder> wrapper,
                                   SaasSubscriptionOrder query,
                                   String startDate, String endDate) {
        if (query != null) {
            if (query.getUserId() != null) {
                wrapper.eq("user_id", query.getUserId());
            }
            if (query.getSubscriptionId() != null) {
                wrapper.eq("subscription_id", query.getSubscriptionId());
            }
            if (query.getPlanId() != null) {
                wrapper.eq("plan_id", query.getPlanId());
            }
            if (StringUtils.hasText(query.getStatus())) {
                wrapper.eq("status", query.getStatus());
            }
            if (StringUtils.hasText(query.getOrderType())) {
                wrapper.eq("order_type", query.getOrderType());
            }
            if (StringUtils.hasText(query.getOrderNo())) {
                wrapper.like("order_no", query.getOrderNo());
            }
        }
        if (StringUtils.hasText(startDate)) {
            wrapper.ge("created_at", parseBound(startDate));
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le("created_at", parseBound(endDate));
        }
    }

    /**
     * 解析时间边界字符串为带偏移量的 {@link OffsetDateTime}。
     *
     * <p>注意遗留行为：偏移量取 {@code ZoneId.systemDefault()}，若服务器时区不是
     * Asia/Shanghai，按北京时间传入的区间会整体偏移。本期保持现状以控制影响面。
     */
    private static OffsetDateTime parseBound(String value) {
        return OffsetDateTime.of(LocalDateTime.parse(value, BOUND_FORMATTER),
                ZoneId.systemDefault().getRules().getOffset(Instant.now()));
    }

    private LambdaQueryWrapper<SaasSubscriptionOrder> buildWrapper(SaasSubscriptionOrder query,
                                                                   String userName,
                                                                   String startDate, String endDate) {
        LambdaQueryWrapper<SaasSubscriptionOrder> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (query.getUserId() != null) {
                wrapper.eq(SaasSubscriptionOrder::getUserId, query.getUserId());
            }
            if (query.getSubscriptionId() != null) {
                wrapper.eq(SaasSubscriptionOrder::getSubscriptionId, query.getSubscriptionId());
            }
            if (query.getPlanId() != null) {
                wrapper.eq(SaasSubscriptionOrder::getPlanId, query.getPlanId());
            }
            if (StringUtils.hasText(query.getStatus())) {
                wrapper.eq(SaasSubscriptionOrder::getStatus, query.getStatus());
            }
            if (StringUtils.hasText(query.getOrderType())) {
                wrapper.eq(SaasSubscriptionOrder::getOrderType, query.getOrderType());
            }
            if (StringUtils.hasText(query.getOrderNo())) {
                wrapper.like(SaasSubscriptionOrder::getOrderNo, query.getOrderNo());
            }
        }
        // 用户名是前端展示字段，订单表本身不存名称，先按名称模糊查出用户 ID 集合再过滤
        if (StringUtils.hasText(userName)) {
            applyUserNameFilter(wrapper, userName);
        }
        // 创建时间范围查询
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(SaasSubscriptionOrder::getCreatedAt, parseBound(startDate));
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(SaasSubscriptionOrder::getCreatedAt, parseBound(endDate));
        }
        wrapper.orderByDesc(SaasSubscriptionOrder::getCreatedAt);
        return wrapper;
    }

    /**
     * 按用户显示名或登录名模糊匹配订单。
     *
     * <p>名称无命中时用恒假条件保证查不到数据，而不是退化成「不过滤」。
     */
    private void applyUserNameFilter(LambdaQueryWrapper<SaasSubscriptionOrder> wrapper, String userName) {
        List<Long> userIds = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .select(SysUser::getId)
                        .and(w -> w.like(SysUser::getDisplayName, userName)
                                .or().like(SysUser::getUsername, userName)))
                .stream().map(SysUser::getId).toList();
        if (userIds.isEmpty()) {
            wrapper.apply("1 = 0");
        } else {
            wrapper.in(SaasSubscriptionOrder::getUserId, userIds);
        }
    }

    private void copyFields(SaasSubscriptionOrder source, SaasSubscriptionOrderVO target) {
        target.setId(source.getId());
        target.setOrderNo(source.getOrderNo());
        target.setSubscriptionId(source.getSubscriptionId());
        target.setUserId(source.getUserId());
        target.setPlanId(source.getPlanId());
        target.setPrevPlanId(source.getPrevPlanId());
        target.setOrderType(source.getOrderType());
        target.setStatus(source.getStatus());
        target.setAmount(source.getAmount());
        target.setCurrency(source.getCurrency());
        target.setDiscountAmount(source.getDiscountAmount());
        target.setFinalAmount(source.getFinalAmount());
        target.setCouponId(source.getCouponId());
        target.setPaymentMethod(source.getPaymentMethod());
        target.setPaymentChannel(source.getPaymentChannel());
        target.setTransactionId(source.getTransactionId());
        target.setPaidAt(source.getPaidAt());
        target.setPeriodStart(source.getPeriodStart());
        target.setPeriodEnd(source.getPeriodEnd());
        target.setInvoiceId(source.getInvoiceId());
        target.setFailureReason(source.getFailureReason());
        target.setRefundReason(source.getRefundReason());
        target.setRefundedAt(source.getRefundedAt());
        target.setMetadata(source.getMetadata());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
    }
}