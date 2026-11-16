package com.example.springai.biz.vo;

import com.example.springai.biz.entity.SaasSubscriptionOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SaasSubscriptionOrderVO extends SaasSubscriptionOrder {

    /** 用户显示名 */
    private String userName;

    /** 套餐名称 */
    private String planName;
}