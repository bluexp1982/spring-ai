package com.example.springai.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.springai.biz.entity.SaasInvoice;
import com.example.springai.biz.mapper.SaasInvoiceMapper;
import com.example.springai.biz.service.SaasInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SaasInvoiceServiceImpl implements SaasInvoiceService {

    private final SaasInvoiceMapper saasInvoiceMapper;

    @Override
    public SaasInvoice getById(Long id) {
        return saasInvoiceMapper.selectById(id);
    }

    @Override
    @Transactional
    public boolean create(SaasInvoice entity) {
        return saasInvoiceMapper.insert(entity) > 0;
    }

    @Override
    @Transactional
    public boolean updateById(SaasInvoice entity) {
        return saasInvoiceMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional
    public boolean deleteById(Long id) {
        return saasInvoiceMapper.deleteById(id) > 0;
    }

    @Override
    public IPage<SaasInvoice> page(int page, int size, SaasInvoice query) {
        LambdaQueryWrapper<SaasInvoice> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (query.getUserId() != null) {
                wrapper.eq(SaasInvoice::getUserId, query.getUserId());
            }
            if (query.getOrderId() != null) {
                wrapper.eq(SaasInvoice::getOrderId, query.getOrderId());
            }
            if (StringUtils.hasText(query.getStatus())) {
                wrapper.eq(SaasInvoice::getStatus, query.getStatus());
            }
            if (StringUtils.hasText(query.getInvoiceNo())) {
                wrapper.like(SaasInvoice::getInvoiceNo, query.getInvoiceNo());
            }
        }
        wrapper.orderByDesc(SaasInvoice::getCreatedAt);
        return saasInvoiceMapper.selectPage(new Page<>(page, size), wrapper);
    }
}