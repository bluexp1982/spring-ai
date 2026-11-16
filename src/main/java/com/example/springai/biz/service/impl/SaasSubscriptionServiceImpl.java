package com.example.springai.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.springai.biz.entity.SaasPlan;
import com.example.springai.biz.entity.SaasSubscription;
import com.example.springai.biz.entity.SysUser;
import com.example.springai.biz.mapper.SaasPlanMapper;
import com.example.springai.biz.mapper.SaasSubscriptionMapper;
import com.example.springai.biz.mapper.SysUserMapper;
import com.example.springai.biz.service.SaasSubscriptionService;
import com.example.springai.biz.support.BizLabels;
import com.example.springai.biz.support.StatsRows;
import com.example.springai.biz.vo.SaasSubscriptionVO;
import com.example.springai.biz.vo.StatsItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaasSubscriptionServiceImpl implements SaasSubscriptionService {

    /** 聚合结果中「分组键」列的别名，需与 select 语句保持一致。 */
    private static final String GROUP_KEY_ALIAS = "group_key";

    /** 聚合结果中「指标值」列的别名。 */
    private static final String METRIC_ALIAS = "metric_value";

    private final SaasSubscriptionMapper saasSubscriptionMapper;
    private final SysUserMapper sysUserMapper;
    private final SaasPlanMapper saasPlanMapper;

    @Override
    public SaasSubscription getById(Long id) {
        return saasSubscriptionMapper.selectById(id);
    }

    @Override
    @Transactional
    public boolean create(SaasSubscription entity) {
        return saasSubscriptionMapper.insert(entity) > 0;
    }

    @Override
    @Transactional
    public boolean updateById(SaasSubscription entity) {
        return saasSubscriptionMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional
    public boolean deleteById(Long id) {
        return saasSubscriptionMapper.deleteById(id) > 0;
    }

    @Override
    public IPage<SaasSubscription> page(int page, int size, SaasSubscription query) {
        return saasSubscriptionMapper.selectPage(new Page<>(page, size), buildWrapper(query, null));
    }

    @Override
    public IPage<SaasSubscriptionVO> pageVO(int page, int size, SaasSubscription query, String userName) {
        // 1. 分页查询实体
        IPage<SaasSubscription> entityPage = saasSubscriptionMapper.selectPage(
                new Page<>(page, size), buildWrapper(query, userName));

        // 2. 收集所有 userId 和 planId
        List<SaasSubscription> records = entityPage.getRecords();
        if (records.isEmpty()) {
            return new Page<SaasSubscriptionVO>().setTotal(entityPage.getTotal())
                    .setCurrent(entityPage.getCurrent()).setSize(entityPage.getSize());
        }

        Set<Long> userIds = records.stream()
                .map(SaasSubscription::getUserId).collect(Collectors.toSet());
        Set<Long> planIds = records.stream()
                .map(SaasSubscription::getPlanId).collect(Collectors.toSet());

        // 3. 批量查询用户和套餐
        Map<Long, String> userNameMap = userIds.isEmpty() ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername(),
                                (a, b) -> a));
        Map<Long, String> planNameMap = planIds.isEmpty() ? Collections.emptyMap()
                : saasPlanMapper.selectBatchIds(planIds).stream()
                        .collect(Collectors.toMap(SaasPlan::getId, SaasPlan::getName, (a, b) -> a));

        // 4. 组装 VO
        List<SaasSubscriptionVO> voList = records.stream().map(entity -> {
            SaasSubscriptionVO vo = new SaasSubscriptionVO();
            // 复制实体字段
            copyFields(entity, vo);
            vo.setUserName(userNameMap.getOrDefault(entity.getUserId(), null));
            vo.setPlanName(planNameMap.getOrDefault(entity.getPlanId(), null));
            return vo;
        }).collect(Collectors.toList());

        Page<SaasSubscriptionVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize());
        voPage.setTotal(entityPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public List<StatsItemVO> stats(String dimension, SaasSubscription query) {
        return switch (canonical(dimension)) {
            case "status" -> statsByStatus(query);
            case "plan" -> statsByPlan(query);
            default -> throw new IllegalArgumentException(
                    "不支持的订阅统计维度：" + dimension + "，可选值：status、plan");
        };
    }

    /** 按订阅状态分组统计数量。 */
    private List<StatsItemVO> statsByStatus(SaasSubscription query) {
        List<Map<String, Object>> rows = saasSubscriptionMapper.selectMaps(statsWrapper("status", query));
        return rows.stream().map(row -> {
            String code = StatsRows.text(StatsRows.pick(row, GROUP_KEY_ALIAS));
            return StatsItemVO.of(code, BizLabels.subscriptionStatusLabel(code),
                    StatsRows.decimal(StatsRows.pick(row, METRIC_ALIAS)));
        }).toList();
    }

    /** 按套餐分组统计数量；先按 plan_id 聚合，再批量换取套餐名，避免 JOIN 与 N+1。 */
    private List<StatsItemVO> statsByPlan(SaasSubscription query) {
        List<Map<String, Object>> rows = saasSubscriptionMapper.selectMaps(statsWrapper("plan_id", query));

        Set<Long> planIds = rows.stream()
                .map(row -> StatsRows.longValue(StatsRows.pick(row, GROUP_KEY_ALIAS)))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> planNameMap = planIds.isEmpty() ? Collections.emptyMap()
                : saasPlanMapper.selectByIds(planIds).stream()
                        .collect(Collectors.toMap(SaasPlan::getId, SaasPlan::getName, (a, b) -> a));

        return rows.stream().map(row -> {
            Long planId = StatsRows.longValue(StatsRows.pick(row, GROUP_KEY_ALIAS));
            String key = planId == null ? "-" : String.valueOf(planId);
            String label = planId == null ? "-" : planNameMap.getOrDefault(planId, "套餐#" + planId);
            return StatsItemVO.of(key, label, StatsRows.decimal(StatsRows.pick(row, METRIC_ALIAS)));
        }).toList();
    }

    /**
     * 构造统计用 QueryWrapper：单表 GROUP BY，走 {@code selectMaps} 返回
     * {@code [{group_key, metric_value}]}，不需要额外的 Mapper XML。
     *
     * <p>过滤条件覆盖 {@link #buildWrapper} 的实体字段部分（不含用户名模糊匹配，
     * 统计接口不接受名称条件），这里用字符串列名（{@code QueryWrapper} 不支持 lambda 列引用）。
     */
    private QueryWrapper<SaasSubscription> statsWrapper(String groupColumn, SaasSubscription query) {
        QueryWrapper<SaasSubscription> wrapper = new QueryWrapper<>();
        wrapper.select(groupColumn + " AS " + GROUP_KEY_ALIAS, "COUNT(*) AS " + METRIC_ALIAS);
        if (query != null) {
            if (query.getUserId() != null) {
                wrapper.eq("user_id", query.getUserId());
            }
            if (query.getPlanId() != null) {
                wrapper.eq("plan_id", query.getPlanId());
            }
            if (StringUtils.hasText(query.getStatus())) {
                wrapper.eq("status", query.getStatus());
            }
        }
        wrapper.groupBy(groupColumn);
        wrapper.orderByDesc(METRIC_ALIAS);
        return wrapper;
    }

    /** 维度归一化：忽略大小写、下划线与连字符，便于模型自由书写。 */
    private static String canonical(String value) {
        return value == null ? ""
                : value.replace("_", "").replace("-", "").trim().toLowerCase(Locale.ROOT);
    }

    private LambdaQueryWrapper<SaasSubscription> buildWrapper(SaasSubscription query, String userName) {
        LambdaQueryWrapper<SaasSubscription> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (query.getUserId() != null) {
                wrapper.eq(SaasSubscription::getUserId, query.getUserId());
            }
            if (query.getPlanId() != null) {
                wrapper.eq(SaasSubscription::getPlanId, query.getPlanId());
            }
            if (StringUtils.hasText(query.getStatus())) {
                wrapper.eq(SaasSubscription::getStatus, query.getStatus());
            }
        }
        // 用户名是前端展示字段，订阅表本身不存名称，先按名称模糊查出用户 ID 集合再过滤
        if (StringUtils.hasText(userName)) {
            applyUserNameFilter(wrapper, userName);
        }
        wrapper.orderByDesc(SaasSubscription::getCreatedAt);
        return wrapper;
    }

    /**
     * 按用户显示名或登录名模糊匹配订阅。
     *
     * <p>名称无命中时用恒假条件保证查不到数据，而不是退化成「不过滤」。
     */
    private void applyUserNameFilter(LambdaQueryWrapper<SaasSubscription> wrapper, String userName) {
        List<Long> userIds = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .select(SysUser::getId)
                        .and(w -> w.like(SysUser::getDisplayName, userName)
                                .or().like(SysUser::getUsername, userName)))
                .stream().map(SysUser::getId).toList();
        if (userIds.isEmpty()) {
            wrapper.apply("1 = 0");
        } else {
            wrapper.in(SaasSubscription::getUserId, userIds);
        }
    }

    private void copyFields(SaasSubscription source, SaasSubscriptionVO target) {
        target.setId(source.getId());
        target.setUserId(source.getUserId());
        target.setPlanId(source.getPlanId());
        target.setStatus(source.getStatus());
        target.setAutoRenew(source.getAutoRenew());
        target.setStartedAt(source.getStartedAt());
        target.setExpiredAt(source.getExpiredAt());
        target.setTrialEndAt(source.getTrialEndAt());
        target.setCancelledAt(source.getCancelledAt());
        target.setPausedAt(source.getPausedAt());
        target.setPausedUntil(source.getPausedUntil());
        target.setCurrentPeriodStart(source.getCurrentPeriodStart());
        target.setCurrentPeriodEnd(source.getCurrentPeriodEnd());
        target.setMetadata(source.getMetadata());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
    }
}