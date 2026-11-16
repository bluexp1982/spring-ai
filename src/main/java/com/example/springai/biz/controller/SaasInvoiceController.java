package com.example.springai.biz.controller;

import com.example.springai.common.PageResult;
import com.example.springai.common.Result;
import com.example.springai.biz.entity.SaasInvoice;
import com.example.springai.biz.service.SaasInvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "发票管理", description = "SaaS 发票的增删改查")
@RestController
@RequestMapping("/api/saas-invoices")
@RequiredArgsConstructor
public class SaasInvoiceController {

    private final SaasInvoiceService saasInvoiceService;

    @Operation(summary = "查询发票详情", description = "根据ID查询发票信息")
    @GetMapping("/{id}")
    public Result<SaasInvoice> getById(@PathVariable Long id) {
        return Result.success(saasInvoiceService.getById(id));
    }

    @Operation(summary = "创建发票", description = "新增一条发票记录")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody SaasInvoice entity) {
        saasInvoiceService.create(entity);
        return Result.success();
    }

    @Operation(summary = "更新发票", description = "根据ID更新发票信息")
    @PutMapping("/{id}")
    public Result<Void> updateById(@PathVariable Long id, @Valid @RequestBody SaasInvoice entity) {
        entity.setId(id);
        saasInvoiceService.updateById(entity);
        return Result.success();
    }

    @Operation(summary = "删除发票", description = "根据ID删除发票")
    @DeleteMapping("/{id}")
    public Result<Void> deleteById(@PathVariable Long id) {
        saasInvoiceService.deleteById(id);
        return Result.success();
    }

    @Operation(summary = "分页查询发票", description = "支持按状态、时间等条件过滤")
    @GetMapping
    public Result<PageResult<SaasInvoice>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            SaasInvoice query) {
        return Result.success(PageResult.of(saasInvoiceService.page(page, size, query)));
    }
}