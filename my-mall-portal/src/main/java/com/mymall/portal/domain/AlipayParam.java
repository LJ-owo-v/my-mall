package com.mymall.portal.domain;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AlipayParam {
    /**
     * 商户订单号（out_trade_no）
     * 商家自定义，必须全局唯一；就是你项目里 generateOrderSn 生成的订单号
     * 支付宝用它标识这笔交易，回调时也会回传这个字段
     */
    private String outTradeNo;

    /**
     * 订单标题（subject）
     * 显示在支付宝收银台和账单里，如"XX商城-订单支付"
     * 不可含特殊符号
     */
    private String subject;

    /**
     * 订单总金额（total_amount）
     * 单位：元，精确到小数点后两位，最小值 0.01
     * 就是订单的 payAmount（应付金额）
     */
    private BigDecimal totalAmount;
}

