package com.example.springai.biz.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.springai.biz.entity.SaasCouponUsage;

public interface SaasCouponUsageService {

    SaasCouponUsage getById(Long id);

    boolean create(SaasCouponUsage entity);

    boolean updateById(SaasCouponUsage entity);

    boolean deleteById(Long id);

    IPage<SaasCouponUsage> page(int page, int size, SaasCouponUsage query);
}