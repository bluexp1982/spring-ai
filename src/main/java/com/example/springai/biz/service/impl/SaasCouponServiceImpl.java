package com.example.springai.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.springai.biz.entity.SaasCoupon;
import com.example.springai.biz.mapper.SaasCouponMapper;
import com.example.springai.biz.service.SaasCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SaasCouponServiceImpl implements SaasCouponService {

    private final SaasCouponMapper saasCouponMapper;

    @Override
    public SaasCoupon getById(Long id) {
        return saasCouponMapper.selectById(id);
    }

    @Override
    @Transactional
    public boolean create(SaasCoupon entity) {
        return saasCouponMapper.insert(entity) > 0;
    }

    @Override
    @Transactional
    public boolean updateById(SaasCoupon entity) {
        return saasCouponMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional
    public boolean deleteById(Long id) {
        return saasCouponMapper.deleteById(id) > 0;
    }

    @Override
    public IPage<SaasCoupon> page(int page, int size, SaasCoupon query) {
        LambdaQueryWrapper<SaasCoupon> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.hasText(query.getCode())) {
                wrapper.like(SaasCoupon::getCode, query.getCode());
            }
            if (StringUtils.hasText(query.getDiscountType())) {
                wrapper.eq(SaasCoupon::getDiscountType, query.getDiscountType());
            }
            if (query.getIsActive() != null) {
                wrapper.eq(SaasCoupon::getIsActive, query.getIsActive());
            }
        }
        wrapper.orderByDesc(SaasCoupon::getCreatedAt);
        return saasCouponMapper.selectPage(new Page<>(page, size), wrapper);
    }
}