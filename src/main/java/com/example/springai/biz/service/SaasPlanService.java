package com.example.springai.biz.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.springai.biz.entity.SaasPlan;

public interface SaasPlanService {

    SaasPlan getById(Long id);

    boolean create(SaasPlan entity);

    boolean updateById(SaasPlan entity);

    boolean deleteById(Long id);

    IPage<SaasPlan> page(int page, int size, SaasPlan query);
}