package com.example.springai.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.springai.biz.entity.SaasPlan;
import com.example.springai.biz.mapper.SaasPlanMapper;
import com.example.springai.biz.service.SaasPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SaasPlanServiceImpl implements SaasPlanService {

    private final SaasPlanMapper saasPlanMapper;

    @Override
    public SaasPlan getById(Long id) {
        return saasPlanMapper.selectById(id);
    }

    @Override
    @Transactional
    public boolean create(SaasPlan entity) {
        return saasPlanMapper.insert(entity) > 0;
    }

    @Override
    @Transactional
    public boolean updateById(SaasPlan entity) {
        return saasPlanMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional
    public boolean deleteById(Long id) {
        return saasPlanMapper.deleteById(id) > 0;
    }

    @Override
    public IPage<SaasPlan> page(int page, int size, SaasPlan query) {
        LambdaQueryWrapper<SaasPlan> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.hasText(query.getName())) {
                wrapper.like(SaasPlan::getName, query.getName());
            }
            if (StringUtils.hasText(query.getCode())) {
                wrapper.eq(SaasPlan::getCode, query.getCode());
            }
            if (query.getIsActive() != null) {
                wrapper.eq(SaasPlan::getIsActive, query.getIsActive());
            }
        }
        wrapper.orderByDesc(SaasPlan::getSortOrder);
        return saasPlanMapper.selectPage(new Page<>(page, size), wrapper);
    }
}