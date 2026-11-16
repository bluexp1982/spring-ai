package com.example.springai.biz.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@TableName(value = "saas_subscriptions", autoResultMap = true)
public class SaasSubscription {

    private Long id;

    private Long userId;

    private Long planId;

    private String status;

    private Boolean autoRenew;

    private OffsetDateTime startedAt;

    private OffsetDateTime expiredAt;

    private OffsetDateTime trialEndAt;

    private OffsetDateTime cancelledAt;

    private OffsetDateTime pausedAt;

    private OffsetDateTime pausedUntil;

    private OffsetDateTime currentPeriodStart;

    private OffsetDateTime currentPeriodEnd;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 逻辑删除标记：0-未删除，1-已删除。
     * 订阅被 saas_subscription_orders / saas_invoices 外键引用，物理删除会违反外键约束，
     * 故删除改为 UPDATE deleted=1，与 mybatis-plus.global-config 中的逻辑删除配置保持一致。
     */
    @TableLogic
    private Integer deleted;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}