package com.example.springai.biz.controller;

import com.example.springai.common.PageResult;
import com.example.springai.common.Result;
import com.example.springai.biz.entity.SaasCouponUsage;
import com.example.springai.biz.service.SaasCouponUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "优惠券使用记录管理", description = "SaaS 优惠券使用记录的增删改查")
@RestController
@RequestMapping("/api/saas-coupon-usages")
@RequiredArgsConstructor
public class SaasCouponUsageController {

    private final SaasCouponUsageService saasCouponUsageService;

    @Operation(summary = "查询使用记录详情", description = "根据ID查询优惠券使用记录")
    @GetMapping("/{id}")
    public Result<SaasCouponUsage> getById(@PathVariable Long id) {
        return Result.success(saasCouponUsageService.getById(id));
    }

    @Operation(summary = "创建使用记录", description = "新增一条使用记录")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody SaasCouponUsage entity) {
        saasCouponUsageService.create(entity);
        return Result.success();
    }

    @Operation(summary = "更新使用记录", description = "根据ID更新使用记录")
    @PutMapping("/{id}")
    public Result<Void> updateById(@PathVariable Long id, @Valid @RequestBody SaasCouponUsage entity) {
        entity.setId(id);
        saasCouponUsageService.updateById(entity);
        return Result.success();
    }

    @Operation(summary = "删除使用记录", description = "根据ID删除使用记录")
    @DeleteMapping("/{id}")
    public Result<Void> deleteById(@PathVariable Long id) {
        saasCouponUsageService.deleteById(id);
        return Result.success();
    }

    @Operation(summary = "分页查询使用记录", description = "支持按优惠券、用户等条件过滤")
    @GetMapping
    public Result<PageResult<SaasCouponUsage>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            SaasCouponUsage query) {
        return Result.success(PageResult.of(saasCouponUsageService.page(page, size, query)));
    }
}