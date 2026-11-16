package com.example.springai.biz.controller;

import com.example.springai.common.PageResult;
import com.example.springai.common.Result;
import com.example.springai.biz.entity.SaasCoupon;
import com.example.springai.biz.service.SaasCouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "优惠券管理", description = "SaaS 优惠券的增删改查")
@RestController
@RequestMapping("/api/saas-coupons")
@RequiredArgsConstructor
public class SaasCouponController {

    private final SaasCouponService saasCouponService;

    @Operation(summary = "查询优惠券详情", description = "根据ID查询优惠券信息")
    @GetMapping("/{id}")
    public Result<SaasCoupon> getById(@PathVariable Long id) {
        return Result.success(saasCouponService.getById(id));
    }

    @Operation(summary = "创建优惠券", description = "新增一条优惠券记录")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody SaasCoupon entity) {
        saasCouponService.create(entity);
        return Result.success();
    }

    @Operation(summary = "更新优惠券", description = "根据ID更新优惠券信息")
    @PutMapping("/{id}")
    public Result<Void> updateById(@PathVariable Long id, @Valid @RequestBody SaasCoupon entity) {
        entity.setId(id);
        saasCouponService.updateById(entity);
        return Result.success();
    }

    @Operation(summary = "删除优惠券", description = "根据ID删除优惠券")
    @DeleteMapping("/{id}")
    public Result<Void> deleteById(@PathVariable Long id) {
        saasCouponService.deleteById(id);
        return Result.success();
    }

    @Operation(summary = "分页查询优惠券", description = "支持按类型、状态等条件过滤")
    @GetMapping
    public Result<PageResult<SaasCoupon>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            SaasCoupon query) {
        return Result.success(PageResult.of(saasCouponService.page(page, size, query)));
    }
}