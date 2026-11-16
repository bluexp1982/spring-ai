package com.example.springai.biz.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Data
@TableName(value = "saas_subscription_orders", autoResultMap = true)
public class SaasSubscriptionOrder {

    private Long id;

    private String orderNo;

    private Long subscriptionId;

    private Long userId;

    private Long planId;

    private Long prevPlanId;

    private String orderType;

    private String status;

    private BigDecimal amount;

    private String currency;

    private BigDecimal discountAmount;

    private BigDecimal finalAmount;

    private Long couponId;

    private String paymentMethod;

    private String paymentChannel;

    private String transactionId;

    private OffsetDateTime paidAt;

    private OffsetDateTime periodStart;

    private OffsetDateTime periodEnd;

    private Long invoiceId;

    private String failureReason;

    private String refundReason;

    private OffsetDateTime refundedAt;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 逻辑删除标记：0-未删除，1-已删除。
     * 订单被 saas_invoices / saas_coupon_usages 外键引用，物理删除会违反外键约束，
     * 故删除改为 UPDATE deleted=1，与 mybatis-plus.global-config 中的逻辑删除配置保持一致。
     */
    @TableLogic
    private Integer deleted;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}