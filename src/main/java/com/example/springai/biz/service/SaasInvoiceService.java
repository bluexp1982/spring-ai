package com.example.springai.biz.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.springai.biz.entity.SaasInvoice;

public interface SaasInvoiceService {

    SaasInvoice getById(Long id);

    boolean create(SaasInvoice entity);

    boolean updateById(SaasInvoice entity);

    boolean deleteById(Long id);

    IPage<SaasInvoice> page(int page, int size, SaasInvoice query);
}