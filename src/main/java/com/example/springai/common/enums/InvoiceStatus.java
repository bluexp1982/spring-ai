package com.example.springai.common.enums;

/**
 * 发票状态: pending / issued / void
 */
public enum InvoiceStatus {
    PENDING("pending"),
    ISSUED("issued"),
    VOID("void");

    private final String value;

    InvoiceStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}