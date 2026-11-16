package com.example.springai.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName("saas_coupon_usages")
public class SaasCouponUsage {

    private Long id;

    private Long couponId;

    private Long orderId;

    private Long userId;

    private BigDecimal discountAmount;

    private OffsetDateTime createdAt;
}