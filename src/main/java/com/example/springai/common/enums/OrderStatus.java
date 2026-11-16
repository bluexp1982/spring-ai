package com.example.springai.common.enums;

/**
 * 订单状态: pending / paid / failed / refunded / expired
 */
public enum OrderStatus {
    PENDING("pending"),
    PAID("paid"),
    FAILED("failed"),
    REFUNDED("refunded"),
    EXPIRED("expired");

    private final String value;

    OrderStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}