package com.example.springai.biz.controller;

import com.example.springai.common.PageResult;
import com.example.springai.common.Result;
import com.example.springai.biz.entity.SaasSubscriptionOrder;
import com.example.springai.biz.service.SaasSubscriptionOrderService;
import com.example.springai.biz.vo.SaasSubscriptionOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "订阅订单管理", description = "SaaS 订阅订单的增删改查")
@RestController
@RequestMapping("/api/saas-subscription-orders")
@RequiredArgsConstructor
public class SaasSubscriptionOrderController {

    private final SaasSubscriptionOrderService saasSubscriptionOrderService;

    @Operation(summary = "查询订单详情", description = "根据ID查询订单信息")
    @GetMapping("/{id}")
    public Result<SaasSubscriptionOrder> getById(@PathVariable Long id) {
        return Result.success(saasSubscriptionOrderService.getById(id));
    }

    @Operation(summary = "创建订单", description = "新增一条订单记录")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody SaasSubscriptionOrder entity) {
        saasSubscriptionOrderService.create(entity);
        return Result.success();
    }

    @Operation(summary = "更新订单", description = "根据ID更新订单信息")
    @PutMapping("/{id}")
    public Result<Void> updateById(@PathVariable Long id, @Valid @RequestBody SaasSubscriptionOrder entity) {
        entity.setId(id);
        saasSubscriptionOrderService.updateById(entity);
        return Result.success();
    }

    @Operation(summary = "删除订单", description = "根据ID删除订单")
    @DeleteMapping("/{id}")
    public Result<Void> deleteById(@PathVariable Long id) {
        saasSubscriptionOrderService.deleteById(id);
        return Result.success();
    }

    @Operation(summary = "分页查询订单",
            description = "支持按状态、订单类型、用户、套餐、时间等条件过滤，返回关联的用户名和套餐名。"
                    + "userName 为模糊匹配（用户显示名或登录名）；套餐按 planId 精确过滤")
    @GetMapping
    public Result<PageResult<SaasSubscriptionOrderVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            SaasSubscriptionOrder query,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(PageResult.of(
                saasSubscriptionOrderService.pageVO(page, size, query, userName, startDate, endDate)));
    }
}