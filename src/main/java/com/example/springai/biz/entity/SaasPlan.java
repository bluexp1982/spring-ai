package com.example.springai.biz.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@TableName(value = "saas_plans", autoResultMap = true)
public class SaasPlan {

    private Long id;

    private String name;

    private String code;

    private String description;

    private BigDecimal price;

    private String currency;

    private String billingCycle;

    private Integer trialDays;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> features;

    private Integer maxUsers;

    private Integer maxStorageMb;

    private Integer sortOrder;

    private Boolean isActive;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}