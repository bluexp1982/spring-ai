package com.example.springai.biz.controller;

import com.example.springai.common.PageResult;
import com.example.springai.common.Result;
import com.example.springai.biz.entity.SaasSubscription;
import com.example.springai.biz.vo.SaasSubscriptionVO;
import com.example.springai.biz.service.SaasSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "订阅管理", description = "SaaS 订阅的增删改查")
@RestController
@RequestMapping("/api/saas-subscriptions")
@RequiredArgsConstructor
public class SaasSubscriptionController {

    private final SaasSubscriptionService saasSubscriptionService;

    @Operation(summary = "查询订阅详情", description = "根据ID查询订阅信息")
    @GetMapping("/{id}")
    public Result<SaasSubscription> getById(@PathVariable Long id) {
        return Result.success(saasSubscriptionService.getById(id));
    }

    @Operation(summary = "创建订阅", description = "新增一条订阅记录")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody SaasSubscription entity) {
        saasSubscriptionService.create(entity);
        return Result.success();
    }

    @Operation(summary = "更新订阅", description = "根据ID更新订阅信息")
    @PutMapping("/{id}")
    public Result<Void> updateById(@PathVariable Long id, @Valid @RequestBody SaasSubscription entity) {
        entity.setId(id);
        saasSubscriptionService.updateById(entity);
        return Result.success();
    }

    @Operation(summary = "删除订阅", description = "根据ID删除订阅")
    @DeleteMapping("/{id}")
    public Result<Void> deleteById(@PathVariable Long id) {
        saasSubscriptionService.deleteById(id);
        return Result.success();
    }

    @Operation(summary = "分页查询订阅",
            description = "支持按状态、用户、套餐等条件过滤，返回关联的用户名和套餐名。"
                    + "userName 为模糊匹配（用户显示名或登录名）；套餐按 planId 精确过滤")
    @GetMapping
    public Result<PageResult<SaasSubscriptionVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            SaasSubscription query,
            @RequestParam(required = false) String userName) {
        return Result.success(PageResult.of(saasSubscriptionService.pageVO(page, size, query, userName)));
    }
}