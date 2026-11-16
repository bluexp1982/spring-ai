package com.example.springai.common.enums;

/**
 * 计费周期: monthly / yearly / quarterly / one_time
 */
public enum BillingCycle {
    MONTHLY("monthly"),
    YEARLY("yearly"),
    QUARTERLY("quarterly"),
    ONE_TIME("one_time");

    private final String value;

    BillingCycle(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}