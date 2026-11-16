package com.example.springai.common.enums;

/**
 * 折扣类型: percentage / fixed
 */
public enum DiscountType {
    PERCENTAGE("percentage"),
    FIXED("fixed");

    private final String value;

    DiscountType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}