package com.example.springai.biz.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.springai.biz.entity.SaasSubscriptionOrder;
import com.example.springai.biz.vo.SaasSubscriptionOrderVO;
import com.example.springai.biz.vo.StatsItemVO;

import java.util.List;

public interface SaasSubscriptionOrderService {

    SaasSubscriptionOrder getById(Long id);

    boolean create(SaasSubscriptionOrder entity);

    boolean updateById(SaasSubscriptionOrder entity);

    boolean deleteById(Long id);

    IPage<SaasSubscriptionOrder> page(int page, int size, SaasSubscriptionOrder query);

    /**
     * 订单分页查询（返回关联的用户名、套餐名）。
     *
     * @param query     实体字段过滤条件（userId / subscriptionId / planId / status / orderType / orderNo），可为 {@code null}
     * @param userName  用户显示名/登录名的模糊匹配条件，为空表示不限
     * @param startDate 创建时间下界，格式 {@code yyyy-MM-dd HH:mm:ss}；为空表示不限
     * @param endDate   创建时间上界，格式 {@code yyyy-MM-dd HH:mm:ss}；为空表示不限
     */
    IPage<SaasSubscriptionOrderVO> pageVO(int page, int size, SaasSubscriptionOrder query,
                                          String userName,
                                          String startDate, String endDate);

    /**
     * 订单统计聚合。
     *
     * @param dimension 统计维度，支持 {@code status}（按订单状态）、{@code orderType}（按订单类型）、
     *                  {@code plan}（按套餐）、{@code month}（按创建月份，北京时间）；
     *                  大小写、下划线、连字符不敏感
     * @param metric    统计指标，支持 {@code count}（笔数）与 {@code amount}（实付金额 final_amount 之和）
     * @param query     过滤条件（userId / subscriptionId / planId / status / orderType / orderNo），可为 {@code null}
     * @param startDate 创建时间下界，格式 {@code yyyy-MM-dd HH:mm:ss}；为空表示不限
     * @param endDate   创建时间上界，格式 {@code yyyy-MM-dd HH:mm:ss}；为空表示不限
     * @return 统计项列表：{@code month} 维度按时间正序（便于展示趋势），其余维度按指标值倒序
     * @throws IllegalArgumentException 维度或指标不受支持时抛出
     */
    List<StatsItemVO> stats(String dimension, String metric,
                            SaasSubscriptionOrder query,
                            String startDate, String endDate);
}
