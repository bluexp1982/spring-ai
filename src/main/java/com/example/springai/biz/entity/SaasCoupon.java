package com.example.springai.biz.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.springai.common.handler.LongArrayTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@TableName(value = "saas_coupons", autoResultMap = true)
public class SaasCoupon {

    private Long id;

    private String code;

    private String name;

    private String discountType;

    private BigDecimal discountValue;

    private BigDecimal minAmount;

    private BigDecimal maxDiscount;

    private Integer maxUses;

    private Integer currentUses;

    private Integer maxUserUses;

    @TableField(typeHandler = LongArrayTypeHandler.class)
    private List<Long> applicablePlanIds;

    private OffsetDateTime validFrom;

    private OffsetDateTime validUntil;

    private Boolean isActive;

    private OffsetDateTime createdAt;
}