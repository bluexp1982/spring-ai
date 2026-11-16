package com.example.springai.biz.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.springai.biz.entity.SaasSubscription;
import com.example.springai.biz.vo.SaasSubscriptionVO;
import com.example.springai.biz.vo.StatsItemVO;

import java.util.List;

public interface SaasSubscriptionService {

    SaasSubscription getById(Long id);

    boolean create(SaasSubscription entity);

    boolean updateById(SaasSubscription entity);

    boolean deleteById(Long id);

    IPage<SaasSubscription> page(int page, int size, SaasSubscription query);

    /**
     * 订阅分页查询（返回关联的用户名、套餐名）。
     *
     * @param query    实体字段过滤条件（userId / planId / status），可为 {@code null}
     * @param userName 用户显示名/登录名的模糊匹配条件，为空表示不限
     */
    IPage<SaasSubscriptionVO> pageVO(int page, int size, SaasSubscription query, String userName);

    /**
     * 订阅统计聚合。
     *
     * @param dimension 统计维度，支持 {@code status}（按订阅状态）与 {@code plan}（按套餐）；
     *                  大小写、下划线、连字符不敏感
     * @param query     过滤条件（userId / planId / status），可为 {@code null} 表示全量统计
     * @return 统计项列表：{@code status} 维度按指标值倒序，{@code plan} 维度同样倒序；
     *         {@code label} 已中文化，可直接映射为图表数据点
     * @throws IllegalArgumentException 维度不受支持时抛出
     */
    List<StatsItemVO> stats(String dimension, SaasSubscription query);
}
