package com.example.springai.biz.controller;

import com.example.springai.common.PageResult;
import com.example.springai.common.Result;
import com.example.springai.biz.entity.SaasPlan;
import com.example.springai.biz.service.SaasPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "套餐管理", description = "SaaS 套餐的增删改查")
@RestController
@RequestMapping("/api/saas-plans")
@RequiredArgsConstructor
public class SaasPlanController {

    private final SaasPlanService saasPlanService;

    @Operation(summary = "查询套餐详情", description = "根据ID查询套餐信息")
    @GetMapping("/{id}")
    public Result<SaasPlan> getById(@PathVariable Long id) {
        return Result.success(saasPlanService.getById(id));
    }

    @Operation(summary = "创建套餐", description = "新增一条套餐记录")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody SaasPlan entity) {
        saasPlanService.create(entity);
        return Result.success();
    }

    @Operation(summary = "更新套餐", description = "根据ID更新套餐信息")
    @PutMapping("/{id}")
    public Result<Void> updateById(@PathVariable Long id, @Valid @RequestBody SaasPlan entity) {
        entity.setId(id);
        saasPlanService.updateById(entity);
        return Result.success();
    }

    @Operation(summary = "删除套餐", description = "根据ID删除套餐")
    @DeleteMapping("/{id}")
    public Result<Void> deleteById(@PathVariable Long id) {
        saasPlanService.deleteById(id);
        return Result.success();
    }

    @Operation(summary = "分页查询套餐", description = "支持按名称、价格等条件过滤")
    @GetMapping
    public Result<PageResult<SaasPlan>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            SaasPlan query) {
        return Result.success(PageResult.of(saasPlanService.page(page, size, query)));
    }
}