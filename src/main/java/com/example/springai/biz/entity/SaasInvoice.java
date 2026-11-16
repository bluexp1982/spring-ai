package com.example.springai.biz.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName("saas_invoices")
public class SaasInvoice {

    private Long id;

    private String invoiceNo;

    private Long orderId;

    private Long subscriptionId;

    private Long userId;

    private String title;

    private String taxIdNo;

    private BigDecimal amount;

    private BigDecimal taxAmount;

    private BigDecimal totalAmount;

    private String status;

    private OffsetDateTime issuedAt;

    private String fileUrl;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}