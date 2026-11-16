package com.example.springai.common.enums;

/**
 * 订阅状态: active / paused / expired / cancelled / in_trial
 */
public enum SubscriptionStatus {
    ACTIVE("active"),
    PAUSED("paused"),
    EXPIRED("expired"),
    CANCELLED("cancelled"),
    IN_TRIAL("in_trial");

    private final String value;

    SubscriptionStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}