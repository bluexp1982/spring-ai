package com.example.springai.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.springai.biz.entity.SaasCouponUsage;
import com.example.springai.biz.mapper.SaasCouponUsageMapper;
import com.example.springai.biz.service.SaasCouponUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SaasCouponUsageServiceImpl implements SaasCouponUsageService {

    private final SaasCouponUsageMapper saasCouponUsageMapper;

    @Override
    public SaasCouponUsage getById(Long id) {
        return saasCouponUsageMapper.selectById(id);
    }

    @Override
    @Transactional
    public boolean create(SaasCouponUsage entity) {
        return saasCouponUsageMapper.insert(entity) > 0;
    }

    @Override
    @Transactional
    public boolean updateById(SaasCouponUsage entity) {
        return saasCouponUsageMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional
    public boolean deleteById(Long id) {
        return saasCouponUsageMapper.deleteById(id) > 0;
    }

    @Override
    public IPage<SaasCouponUsage> page(int page, int size, SaasCouponUsage query) {
        LambdaQueryWrapper<SaasCouponUsage> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (query.getCouponId() != null) {
                wrapper.eq(SaasCouponUsage::getCouponId, query.getCouponId());
            }
            if (query.getOrderId() != null) {
                wrapper.eq(SaasCouponUsage::getOrderId, query.getOrderId());
            }
            if (query.getUserId() != null) {
                wrapper.eq(SaasCouponUsage::getUserId, query.getUserId());
            }
        }
        wrapper.orderByDesc(SaasCouponUsage::getCreatedAt);
        return saasCouponUsageMapper.selectPage(new Page<>(page, size), wrapper);
    }
}