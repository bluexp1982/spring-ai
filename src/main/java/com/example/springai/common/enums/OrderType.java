package com.example.springai.common.enums;

/**
 * 订单类型: new / renewal / upgrade / downgrade / refund / cancel
 */
public enum OrderType {
    NEW("new"),
    RENEWAL("renewal"),
    UPGRADE("upgrade"),
    DOWNGRADE("downgrade"),
    REFUND("refund"),
    CANCEL("cancel");

    private final String value;

    OrderType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}