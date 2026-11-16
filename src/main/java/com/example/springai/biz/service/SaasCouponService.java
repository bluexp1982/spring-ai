package com.example.springai.biz.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.springai.biz.entity.SaasCoupon;

public interface SaasCouponService {

    SaasCoupon getById(Long id);

    boolean create(SaasCoupon entity);

    boolean updateById(SaasCoupon entity);

    boolean deleteById(Long id);

    IPage<SaasCoupon> page(int page, int size, SaasCoupon query);
}